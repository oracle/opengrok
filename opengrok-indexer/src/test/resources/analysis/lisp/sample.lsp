;;   Copyright 2013 Google Inc.
;;
;;   Licensed under the Apache License, Version 2.0 (the "License");
;;   you may not use this file except in compliance with the License.
;;   You may obtain a copy of the License at
;;
;;       http://www.apache.org/licenses/LICENSE-2.0
;;
;;   Unless required by applicable law or agreed to in writing, software
;;   distributed under the License is distributed on an "AS IS" BASIS,
;;   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;   See the License for the specific language governing permissions and
;;   limitations under the License.

(define-test test-mapcar-basics
    "We can apply a function to each member
     of a list using mapcar."
  (defun times-two (x) (* x 2))
  (assert-equal ____ (mapcar #'times-two '(1 2 3)))
  (assert-equal ____ (mapcar #'first '((3 2 1)
                                      ("little" "small" "tiny")
                                      ("pigs" "hogs" "swine")))))


(define-test test-mapcar-multiple-lists
    "The mapcar function can be applied to
     more than one list. It applies a function
     to successive elements of the lists."
  (assert-equal ____ (mapcar #'* '(1 2 3) '(4 5 6)))
  (assert-equal ____ (mapcar #'list '("lisp" "are") '("koans" "fun"))))


(define-test test-transpose-using-mapcar
    "Replace the usage of WRONG-FUNCTION in 'transpose' with the
     correct lisp function (don't forget the #')."
  (defun WRONG-FUNCTION-1 (&rest rest) '())
  (defun transpose (L) (apply #'mapcar (cons #'WRONG-FUNCTION-1 L)))
  (assert-equal '((1 4 7)
                  (2 5 8)
                  (3 6 9))
                (transpose '((1 2 3)
                             (4 5 6)
                             (7 8 9))))
  (assert-equal '(("these" "pretzels" "are")
                  ("making" "me" "thirsty"))
                (transpose '(("these" "making")
                             ("pretzels" "me")
                             ("are" "thirsty")))))


(define-test test-reduce-basics
    "The reduce function combines the elements
     of a list, from left to right, by applying
     a binary function to the list elements."
  (assert-equal ___  (reduce #'+ '(1 2 3 4)))
  (assert-equal ___ (reduce #'expt '(2 3 2))))


(define-test test-reduce-right-to-left
    "The keyword :from-end allows us to apply
     reduce from right to left."
  (assert-equal ___ (reduce #'+ '(1 2 3 4) :from-end t))
  (assert-equal ___ (reduce #'expt '(2 3 2) :from-end t)))


(define-test test-reduce-with-initial-value
    "We can supply an initial value to reduce."
  (assert-equal ___ (reduce #'expt '(10 21 34 43) :initial-value 1))
  (assert-equal ___ (reduce #'expt '(10 21 34 43) :initial-value 0)))


(defun WRONG-FUNCTION-2 (a b) (a))
(defun WRONG-FUNCTION-3 (a b) (a))

(define-test test-mapcar-and-reduce
    "mapcar and reduce are a powerful combination.
     insert the correct function names, instead of WRONG-FUNCTION-X
     to define an inner product."
  (defun inner (x y)
    (reduce #'WRONG-FUNCTION-2 (mapcar #'WRONG-FUNCTION-3 x y)))
  (assert-equal 32 (inner '(1 2 3) '(4 5 6)))
  (assert-equal 310 (inner '(10 20 30) '(4 3 7))))
