#
# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License (the "License").
# You may not use this file except in compliance with the License.
#
# See LICENSE.txt included in this distribution for the specific
# language governing permissions and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at LICENSE.txt.
# If applicable, add the following below this CDDL HEADER, with the
# fields enclosed by brackets "[]" replaced with your own identifying
# information: Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
CREATE TABLE foo (
   col1 INT NOT NULL PRIMARY KEY,
   col2 VARCHAR(100)
);

SELECT * FROM foo;

INSERT INTO foo (col1, col2) VALUES (1, 'something'), (5, 'something else');

-- This is an SQL comment with a email address: username@example.com
DELETE FROM foo WHERE id=5;

SELECT COUNT(col1) FROM foo;

-- This en an SQL comment with strange characters: <, > and &
DROP TABLE "foo";

CREATE TABLE "foo""";

/* Other supported comment */
SELECT 123.45 + 543E-2 FROM DUAL;

/* /* Comment inside comment */ */

-- Text values:
INSERT INTO foo(col2) VALUES ('this'), ('and this'), ('and '' too');
