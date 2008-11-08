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

void MyClass::print() {
    std::cout << myname.c_str() << std::endl;
}
