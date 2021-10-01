DIR:sh=uname -p

all: $(DIR)/testprog $(DIR)/libfoobar.so

$(DIR)/libfoobar.so: $(DIR)/foobar.o
	$(LINK.c) -o $(DIR)/libfoobar.so -G $(DIR)/foobar.o

$(DIR)/testprog: $(DIR)/main.o
	$(LINK.c) -o $(DIR)/testsprog $(DIR)/main.o

$(DIR):
	mkdir $(DIR)

$(DIR)/main.o: $(DIR) main.c header.h
	$(COMPILE.c) -o $(DIR)/main.o main.c

$(DIR)/foobar.o: $(DIR) foobar.c
	$(COMPILE.c) -o $(DIR)/foobar.o -Kpic foobar.c

