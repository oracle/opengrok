#include "bkexlib.hpp"
#include <random>

int getRandomNumber(void)
{
    return 4;  /* chosen by fair dice roll. */
               /* guaranteed to be random. */
}

int butActuallyThough(void)
{
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, RAND_MAX);
    return dis(gen);
}
