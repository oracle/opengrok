using System;

namespace MyNamespace
{
    /// <summary>
    /// summary
    /// </summary>
    [Tag]
    class TopClass {
        public int M1() { }

        public static void M2() {
            //public static int MERR() {}
        }

        /// <summary>
        /// sum
        /// </summary>
        public void M3() 
        {
            /* hi 
                public static int MERR() {
                }
            */
        }

        [Tag]
        private T M4<T>(T p) where T : new()
        {
        }

        public class InnerClass 
        {
            private string M5(int x) {  
                return null;
            }
        }

    {

}
