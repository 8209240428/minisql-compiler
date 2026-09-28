CREATE TABLE student(id INT, name VARCHAR, age INT);
INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);
SELECT id, name FROM student WHERE age >= 18 AND id <> 3;
DELETE FROM student WHERE id = 1;
SELECT * FROM student WHERE a = 1 OR b = 2 AND c = 3;
SELECT * FROM student WHERE (a = 1 OR b = 2) AND c = 3;
SELECT name FROM student WHERE 1 = 1 AND age > 10 + 8;
SELECT name FROM student WHERE age > 18 AND;
SELECT * FROM nosuch;
