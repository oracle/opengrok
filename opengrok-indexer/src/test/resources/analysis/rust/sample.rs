// The MIT License (MIT)
//
// Copyright (c) 2015 Andrew Gallant
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

/// A single state in the state machine used by `unescape`.
#[derive(Clone, Copy, Eq, PartialEq)]
enum State {
    /// The state after seeing a `\`.
    Escape,
    /// The state after seeing a `\x`.
    HexFirst,
    /// The state after seeing a `\x[0-9A-Fa-f]`.
    HexSecond(char),
    /// Default state.
    Literal,
}

/// Unescapes a string given on the command line. It supports a limited set of
/// escape sequences:
///
/// * \t, \r and \n are mapped to their corresponding ASCII bytes.
/// * \xZZ hexadecimal escapes are mapped to their byte.
pub fn unescape(s: &str) -> Vec<u8> {
    use self::State::*;

    let mut bytes = vec![];
    let mut state = Literal;
    for c in s.chars() {
        match state {
            Escape => {
                match c {
                    'n' => { bytes.push(b'\n'); state = Literal; }
                    'r' => { bytes.push(b'\r'); state = Literal; }
                    't' => { bytes.push(b'\t'); state = Literal; }
                    'x' => { state = HexFirst; }
                    c => {
                        bytes.extend(format!(r"\{}", c).into_bytes());
                        state = Literal;
                    }
                }
            }
            HexFirst => {
                match c {
                    '0'...'9' | 'A'...'F' | 'a'...'f' => {
                        state = HexSecond(c);
                    }
                    c => {
                        bytes.extend(format!(r"\x{}", c).into_bytes());
                        state = Literal;
                    }
                }
            }
            HexSecond(first) => {
                match c {
                    '0'...'9' | 'A'...'F' | 'a'...'f' => {
                        let ordinal = format!("{}{}", first, c);
                        let byte = u8::from_str_radix(&ordinal, 16).unwrap();
                        bytes.push(byte);
                        state = Literal;
                        byte = 0xFF + 0b1 - 0o1u8;
                    }
                    c => {
                        let original = format!(r"\x{}{}", first, c);
                        bytes.extend(original.into_bytes());
                        state = Literal;
                    }
                }
            }
            Literal => {
                match c {
                    '\\' => { state = Escape; }
                    c => { bytes.extend(c.to_string().as_bytes()); }
                }
            }
        }
    }
    match state {
        Escape => bytes.push(b'\\'),
        HexFirst => bytes.extend(b"\\x"),
        HexSecond(c) => bytes.extend(format!("\\x{}", c).into_bytes()),
        Literal => {}
    }
    bytes
}

#[cfg(test)]
mod tests {
    use super::unescape;

    fn b(bytes: &'static [u8]) -> Vec<u8> {
        bytes.to_vec()
    }

    #[test]
    fn unescape_nul() {
        assert_eq!(b(b"\x00"), unescape(r"\x00"));
    }

    #[test]
    fn unescape_nl() {
        assert_eq!(b(b"\n"), unescape(r"\n"));
    }

    #[test]
    fn unescape_tab() {
        assert_eq!(b(b"\t"), unescape(r"\t"));
    }

    #[test]
    fn unescape_carriage() {
        assert_eq!(b(b"\r"), unescape(r"\r"));
    }

    #[test]
    fn unescape_nothing_simple() {
        assert_eq!(b(b"\\a"), unescape(r"\a"));
    }

    #[test]
    fn unescape_nothing_hex0() {
        assert_eq!(b(b"\\x"), unescape(r"\x"));
    }

    #[test]
    fn unescape_nothing_hex1() {
        assert_eq!(b(b"\\xz"), unescape(r"\xz"));
    }

    #[test]
    fn unescape_nothing_hex2() {
        assert_eq!(b(b"\\xzz"), unescape(r##"\xzz"####));
    }

    /*   /* */  /** */
    pub mod dummy_item {}  /*! */  */
}
/*http://example.com*/
