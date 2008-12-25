;
; CDDL HEADER START
;
; The contents of this file are subject to the terms of the
; Common Development and Distribution License (the "License").
; You may not use this file except in compliance with the License.
;
; See LICENSE.txt included in this distribution for the specific
; language governing permissions and limitations under the License.
;
; When distributing Covered Code, include this CDDL HEADER in each
; file and include the License file at LICENSE.txt.
; If applicable, add the following below this CDDL HEADER, with the
; fields enclosed by brackets "[]" replaced with your own identifying
; information: Portions Copyright [yyyy] [name of copyright owner]
;
; CDDL HEADER END
;
(defun foo-bar () ( setq variable 5 ))
(defun foo-bar () ( setq str_variable "string value" ))
(foo-bar)
; Multi line comment, with embedded strange characters: < > &,
; email address: testuser@example.com and even an URL:
; http://www.example.com/index.html and a file name and a path:
; <example.cpp> and </usr/local/example.h>.
; Ending with an email address: username@example.com
