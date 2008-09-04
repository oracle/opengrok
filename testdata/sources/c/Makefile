DIR:sh=uname -p

$(DIR)/testprog: $(DIR)/main.o
	$(LINK.c) -o $(DIR)/testsprog $(DIR)/main.o

$(DIR):
	mkdir $(DIR)

$(DIR)/main.o: $(DIR) main.c header.h
	$(COMPILE.c) -o $(DIR)/main.o main.c
