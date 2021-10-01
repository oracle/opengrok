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
