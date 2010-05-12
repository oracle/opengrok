!
! CDDL HEADER START
!
! The contents of this file are subject to the terms of the
! Common Development and Distribution License (the "License").
! You may not use this file except in compliance with the License.
!
! See LICENSE.txt included in this distribution for the specific
! language governing permissions and limitations under the License.
!
! When distributing Covered Code, include this CDDL HEADER in each
! file and include the License file at LICENSE.txt.
! If applicable, add the following below this CDDL HEADER, with the
! fields enclosed by brackets "[]" replaced with your own identifying
! information: Portions Copyright [yyyy] [name of copyright owner]
!
! CDDL HEADER END
!
PROGRAM hello
!
! This is a comment
!

  REAL, PARAMETER :: start = 0.0
  REAL, PARAMETER :: end = 12.0
  REAL count, incr ! In line comment

  count = start
  incr = 2.0
  DO WHILE  (count .lt. end)
    CALL say_hello( 'World' )
    IF (count /= 8) THEN
      WRITE(*, fmt="(F8.0)") count
    END IF
    count = count + incr
  END DO

  CONTAINS 

  SUBROUTINE say_hello(who)
    CHARACTER(LEN=*), INTENT(in) :: who

    PRINT *, 'Hello ', who
  END SUBROUTINE say_hello

END PROGRAM hello
