// compile me with g++
/* this is sample comment } */
#include <string>
#include <vector>
#include <iostream>

#define TEST(x) (x)

class SomeClass {
public:
    SomeClass() /* I'm constructor */
        : attr_(0)
    {
        std::cout << "Hello" << std::endl;
    }
    
    ~SomeClass() // destructor
    {
        std::cout << "Bye" << std::endl;
    }
    
    int MemberFunc(int a, int b) const {
        // some member function
        return a + b;
    }
    
    int operator++(int) {
        return attr_++;
    }
    
    template<typename T>
    size_t TemplateMember(std::vector<T>& v) {
        return v.size();
    }
    
private:
    int attr_;
};

namespace ns1 {
    
    class NamespacedClass {
    public:
        static void SomeFunc(const std::string& arg) {
            std::cout << arg;
        }
    };
    
    namespace ns2 {

        int foo(int a, int b) {
            SomeClass t;
            return t.MemberFunc(TEST(a), TEST(b));
        }

    }
}

int bar(int x /* } */)
{
    // another function
    int d;
    int f;
    std::cout << TEST("test { message|$#@$!!#") << std::endl;
    d = foo(2, 4);
    f = foo(x, d);

    /* return
        some
         rubish
    */
    return d+f;
}

// main function
int main(int argc, char *argv[]) {
    SomeClass c;
    int res;
    std::cout << "this is just a {sample}}" << std::endl;

    res = bar(20);
    std::cout << "result = {" << res << "}" << std::endl;

    std::cout << c.MemberFunc(1, 2) << std::endl;
    std::cout << c++ << std::endl;
    
    return 0; }

