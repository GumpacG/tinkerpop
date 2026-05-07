#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
import aiohttp
import asyncio
import atexit
import sys
import threading
import weakref

if sys.version_info >= (3, 11):
    import asyncio as async_timeout
else:
    import async_timeout

__author__ = 'Lyndon Bauto (lyndonb@bitquilltech.com)'

# Default upper bound (seconds) we will wait for the background event loop
# to flush in-flight work and shut down cleanly. Bounded so that close()
# can never deadlock even if the loop or thread is wedged.
_DEFAULT_CLOSE_TIMEOUT = 5.0

# Track live transports so that an atexit hook can close any the user
# forgot to close, while the interpreter (and the daemon background
# threads) are still alive.
_LIVE_TRANSPORTS = weakref.WeakSet()
_LIVE_TRANSPORTS_LOCK = threading.Lock()


def _atexit_close_live_transports():
    """Best-effort close of any transports still alive at interpreter exit.

    Runs before daemon threads are torn down, so background loops still
    process the close coroutine. Any failure here is swallowed; we are
    on the way out either way.
    """
    with _LIVE_TRANSPORTS_LOCK:
        transports = list(_LIVE_TRANSPORTS)
    for t in transports:
        try:
            t.close(timeout=_DEFAULT_CLOSE_TIMEOUT)
        except Exception:
            pass


atexit.register(_atexit_close_live_transports)


class AiohttpSyncStream:
    """Wraps aiohttp's async StreamReader as a synchronous file-like object.
    read(n) blocks until exactly n bytes are available from the HTTP response."""

    def __init__(self, response, transport, read_timeout):
        self._response = response
        self._transport = transport
        self._read_timeout = read_timeout

    def read(self, n):
        async def _read():
            async with async_timeout.timeout(self._read_timeout):
                return await self._response.content.readexactly(n)
        return self._transport._run_until_complete(_read())


class AiohttpHTTPTransport:

    def __init__(self, call_from_event_loop=None, read_timeout=None, write_timeout=None, **kwargs):
        # Start event loop and initialize client session and response to None
        self._loop = asyncio.new_event_loop()
        self._call_from_event_loop = call_from_event_loop is not None and call_from_event_loop
        self._thread = None
        self._closed = False
        self._close_lock = threading.Lock()
        if self._call_from_event_loop:
            # When called from within a running event loop (e.g., Jupyter),
            # run our own event loop on a dedicated daemon thread and dispatch
            # coroutines via run_coroutine_threadsafe.
            def run_loop(loop):
                asyncio.set_event_loop(loop)
                loop.run_forever()

            self._thread = threading.Thread(target=run_loop, args=(self._loop,), daemon=True)
            self._thread.start()

        self._client_session = None
        self._http_req_resp = None
        self._enable_ssl = False
        self._url = None

        # Set all inner variables to parameters passed in.
        self._aiohttp_kwargs = kwargs
        self._write_timeout = write_timeout
        self._read_timeout = read_timeout
        # max_content_length is no longer enforced with streaming deserialization, but pop it
        # to prevent it from leaking to aiohttp as an unknown kwarg
        self._aiohttp_kwargs.pop("max_content_length", None)
        if "ssl_options" in self._aiohttp_kwargs:
            self._ssl_context = self._aiohttp_kwargs.pop("ssl_options")
            self._enable_ssl = True

        with _LIVE_TRANSPORTS_LOCK:
            _LIVE_TRANSPORTS.add(self)

    def _run_until_complete(self, coro):
        if self._call_from_event_loop:
            # Submit to the background loop and wait synchronously. The
            # try/except cancels the in-flight coroutine if the caller
            # thread is interrupted (e.g. KeyboardInterrupt in Jupyter),
            # so we don't leave a ghost HTTP request running on the
            # background loop.
            future = asyncio.run_coroutine_threadsafe(coro, self._loop)
            try:
                return future.result()
            except BaseException:
                future.cancel()
                raise

        return self._loop.run_until_complete(coro)

    def __del__(self):
        # Best-effort fallback only. We must not block the finalizer (no
        # Thread.join, no waiting on the background loop) because __del__
        # can run during interpreter shutdown where ordering is undefined
        # and joining a daemon thread can deadlock. The atexit hook above
        # is the primary safety net for the "user forgot to close" case.
        if sys.is_finalizing():
            return
        try:
            self.close(timeout=0.1)
        except Exception:
            pass

    def connect(self, url, headers=None):
        self._url = url
        # Inner function to perform async connect.
        async def async_connect():
            # Start client session and use it to send all HTTP requests. Headers can be set here.
            if self._enable_ssl:
                # ssl context is established through tcp connector
                tcp_conn = aiohttp.TCPConnector(ssl_context=self._ssl_context)
                self._client_session = aiohttp.ClientSession(connector=tcp_conn,
                                                             headers=headers, loop=self._loop)
            else:
                self._client_session = aiohttp.ClientSession(headers=headers, loop=self._loop)

        # Execute the async connect synchronously.
        self._run_until_complete(async_connect())

    def write(self, message):
        # Inner function to perform async write.
        async def async_write():
            # To pass url into message for request authentication processing
            message.update({'url': self._url})
            if message['auth']:
                message['auth'](message)

            async with async_timeout.timeout(self._write_timeout):
                self._http_req_resp = await self._client_session.post(url=self._url,
                                                                      data=message['payload'],
                                                                      headers=message['headers'],
                                                                      **self._aiohttp_kwargs)

        # Execute the async write synchronously.
        self._run_until_complete(async_write())

    def get_stream(self):
        """Returns a synchronous file-like object for the HTTP response body."""
        return AiohttpSyncStream(self._http_req_resp, self, self._read_timeout)

    @property
    def content_type(self):
        """Returns the Content-Type header of the HTTP response."""
        if self._http_req_resp is not None:
            return self._http_req_resp.headers.get('content-type', '')
        return ''

    @property
    def status_code(self):
        """Returns the HTTP status code of the response."""
        if self._http_req_resp is not None:
            return self._http_req_resp.status
        return None

    def read_body(self):
        """Read the entire HTTP response body as bytes."""
        async def _read():
            async with async_timeout.timeout(self._read_timeout):
                return await self._http_req_resp.read()
        return self._run_until_complete(_read())

    def close(self, timeout=_DEFAULT_CLOSE_TIMEOUT):
        """Close the transport and release all resources.

        Idempotent and bounded: this method will return within roughly
        ``timeout`` seconds even if the background event loop or aiohttp
        session is wedged. Resources that cannot be reclaimed in time are
        leaked rather than blocking the caller.
        """
        # Idempotency: only one caller does the work; subsequent calls
        # are no-ops. Without this, __del__ + explicit close, or atexit
        # + close, can race and re-enter the threaded teardown path on a
        # closed loop.
        with self._close_lock:
            if self._closed:
                return
            self._closed = True

        # Inner function to perform async close.
        async def async_close():
            if self._client_session is not None and not self._client_session.closed:
                await self._client_session.close()
                self._client_session = None

        if self._call_from_event_loop:
            self._close_threaded(async_close, timeout)
        else:
            self._close_inline(async_close)

    def _close_inline(self, async_close):
        """Close path used when the transport owns the caller's loop."""
        if not self._loop.is_closed():
            try:
                self._run_until_complete(async_close())
            except Exception:
                pass
            try:
                self._loop.close()
            except Exception:
                pass

    def _close_threaded(self, async_close, timeout):
        """Close path used when the loop runs on a dedicated background thread.

        Each step is bounded and guarded so that a hung loop, a dead
        thread, or a double-close cannot deadlock or raise out of close().
        """
        # 1) Try to close the aiohttp session on the background loop, but
        #    don't wait forever -- if the loop is wedged we accept the
        #    leak rather than block the caller.
        if not self._loop.is_closed():
            try:
                future = asyncio.run_coroutine_threadsafe(async_close(), self._loop)
                try:
                    future.result(timeout=timeout)
                except (asyncio.TimeoutError, TimeoutError):
                    future.cancel()
                except BaseException:
                    future.cancel()
            except RuntimeError:
                # Loop already stopped/closed between our check and the call.
                pass

        # 2) Stop the background loop. Guard against the loop already
        #    being closed (e.g. by another close() racing us, or by
        #    interpreter shutdown finalising the loop first).
        try:
            if not self._loop.is_closed():
                self._loop.call_soon_threadsafe(self._loop.stop)
        except RuntimeError:
            pass

        # 3) Wait for the background thread to exit, but only briefly.
        #    A still-alive thread here means the loop is genuinely stuck;
        #    we don't try to force-kill it (Python has no safe way to do
        #    so) -- the daemon flag will let the interpreter reap it on
        #    exit.
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=timeout)

        # 4) Only close the loop if its thread has actually exited.
        #    Calling loop.close() on a still-running loop raises; better
        #    to leak the loop object than to raise out of close().
        thread_done = self._thread is None or not self._thread.is_alive()
        if thread_done and not self._loop.is_closed():
            try:
                self._loop.close()
            except Exception:
                pass

    @property
    def closed(self):
        # Connection is closed when client session is closed.
        return self._client_session.closed
