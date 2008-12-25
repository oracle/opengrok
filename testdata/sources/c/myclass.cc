#include <iostream>
#include <string>

class MyClass {
public:
    MyClass() {
        std::cout << "constructor" << std::endl;
    }
    virtual ~MyClass() {
        std::cout << "destructor" << std::endl;
    }

    void print();

private:
    std::string myname;

};

   /*
   Multi line comment, with embedded strange characters: < > &,
   email address: testuser@example.com and even an URL:
   http://www.example.com/index.html and a file name and a path:
   <example.cpp> and </usr/local/example.h>.
   Ending with an email address: username@example.com
   */

// C++ also supports single line comments
void MyClass::print() {
    std::cout << myname.c_str() << std::endl;
}
