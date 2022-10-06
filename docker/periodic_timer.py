"""
Helper class for start.py
"""

import threading
import time


class PeriodicTimer:
    """
    Helper class to facilitate waiting for periodic events.
    Requires the start() function to be called first.
    """

    def __init__(self, interval):
        """
        :param interval: interval in seconds
        """
        self._interval = interval
        self._flag = 0
        self._cv = threading.Condition()

    def start(self):
        """
        Start the notification thread.
        """
        threading.Thread(target=self.run, daemon=True).start()

    def run(self):
        """
        Run the timer and notify waiting threads after each interval
        """
        while True:
            time.sleep(self._interval)
            self.notify_all()

    def wait_for_tick(self):
        """
        Wait for the next tick of the timer
        """
        with self._cv:
            last_flag = self._flag
            while last_flag == self._flag:
                self._cv.wait()

    def notify_all(self):
        """
        Notify all listeners, possibly out of band.
        """
        with self._cv:
            self._flag ^= 1
            self._cv.notify_all()
