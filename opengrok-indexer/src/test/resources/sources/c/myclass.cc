#include <iostream>
#include <string>
#include </path/to/header.h>
#include <header.h>
#include "myhead.hh"

class MyClass {
public:
    MyClass() {
        std::cout << "con\\str'u'ctor" << std::endl;
    }
    virtual ~MyClass() {
        std::cout << "\"destructor\"" << std::endl;
    }

    void print();

private:
    std::string myname;

};

   /*
   Multi line comment, with embedded strange characters: < > &,
   email address: testuser@example.com and even an URL:
   http://www.example.com/index.html and a file name and a path:
   <example.cpp> and </usr/local/example.cpp>,
   example2.cpp and /usr/local/example2.cpp.
   Ending with an email address: username@example.com
   */

// C++ also supports single line comments
void MyClass::print() {
    char c = '''';
    int i = 123;
    std::cout << myname.c_str() << std::endl;
}
