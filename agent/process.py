"""Bounded subprocess capture with timeouts and cleanup of our process tree."""
import os
import queue
import subprocess
import threading
import time
from collections import deque


def terminate(process):
    if process.poll() is not None:
        return
    if os.name == 'nt':
        try:
            subprocess.run([os.path.join(os.environ.get('SystemRoot', r'C:\Windows'), 'System32', 'taskkill.exe'),
                            '/PID', str(process.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
        finally:
            if process.poll() is None:
                process.kill()
    else:
        import signal
        os.killpg(process.pid, signal.SIGKILL)


def execute(command, cwd, env, timeout, redactor, input_text=None, on_line=None):
    process = subprocess.Popen(command, cwd=cwd, env=env,
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding='utf-8', errors='replace',
        start_new_session=os.name != 'nt')
    lines = queue.Queue(maxsize=256)
    stopped = threading.Event()

    def read(stream, name):
        try:
            while not stopped.is_set():
                line = stream.readline(1024 * 1024)
                item = (name, line) if line else (name, None)
                while not stopped.is_set():
                    try:
                        lines.put(item, timeout=0.1)
                        break
                    except queue.Full:
                        continue
                if not line:
                    break
        finally:
            stream.close()

    readers = [threading.Thread(target=read, args=(stream, name), daemon=True)
               for stream, name in ((process.stdout, 'stdout'), (process.stderr, 'stderr'))]
    for reader in readers:
        reader.start()
    tail = deque(maxlen=30)
    deadline, finished = time.monotonic() + timeout, 0
    try:
        if input_text:
            process.stdin.write(input_text)
        process.stdin.close()
        while finished < 2:
            if time.monotonic() >= deadline:
                raise TimeoutError('Process exceeded configured timeout')
            try:
                stream, line = lines.get(timeout=0.1)
            except queue.Empty:
                continue
            if line is None:
                finished += 1
                continue
            tail.append(redactor.text(line)[-2000:])
            if on_line and stream == 'stdout':
                on_line(line)
        return process.wait(timeout=max(0.1, deadline - time.monotonic())), ''.join(tail)[-4000:]
    finally:
        stopped.set()
        terminate(process)
        process.wait(timeout=10)
        for reader in readers:
            reader.join(timeout=2)
