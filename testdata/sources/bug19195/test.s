/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://www.opensolaris.org/os/licensing.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
 */

/* first function definition */

	ENTRY_NP(foo)
	set	nwindows, %g1
	ld	[%g1], %g1
	mov	%g1, %g2

1:
	save	%sp, -WINDOWSIZE, %sp
	brnz	%g2, 1b
	dec	%g2

	mov	%g1, %g2
2:
	restore
	brnz	%g2, 2b
	dec	%g2

	retl
	nop

	SET_SIZE(foo)

/* 2nd function definition */

	ENTRY(bar)
	rdpr	%otherwin, %g1
	brz	%g1, 3f
	clr	%g2
1:
	save	%sp, -WINDOWSIZE, %sp
	rdpr	%otherwin, %g1
	brnz	%g1, 1b
	add	%g2, 1, %g2
2:
	sub	%g2, 1, %g2		! restore back to orig window
	brnz	%g2, 2b
	restore
3:
	retl
	nop
	SET_SIZE(bar)

/* definitions of the same function twice */

	ENTRY2(_fce,__fce)
	cmp	%o0, ERESTART
	be,a	1f
	mov	EINTR, %o0
1:
	save	%sp, -SA(MINFRAME), %sp
	call	___errno
	nop
	st	%i0, [%o0]
	restore
	retl
	mov	-1, %o0

	SET_SIZE(_fce)
	SET_SIZE(__fce)
