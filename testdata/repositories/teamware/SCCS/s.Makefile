h26052
s 00003/00000/00008
d D 1.2 08/08/12 22:11:12 trond 2 1
c Added lint target
e
s 00008/00000/00000
d D 1.1 08/08/12 22:09:23 trond 1 0
c date and time created 08/08/12 22:09:23 by trond
e
u
U
f e 0
t
T
I 1
testprog: main.o
	$(LINK.c) -o testsprog main.o

main.o: main.c header.h
	$(COMPILE.c) main.c

clean:
	$(RM) main.o testprog
I 2

lint: main.c header.h
	$(LINT) main.c
E 2
E 1
