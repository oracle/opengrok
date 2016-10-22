//! opengrok rust test by Nikolay Denev <ndenev@gmail.com>

//
// Random Rust source
//

/// Count enum
enum Count {
    One,
    Two(u64),
    Three(String),
    Four { id: u64, name: String },
}

struct Counter<T> where T: ShowItem {
    item: T,
}

impl<T> Counter<T> where T: ShowItem {
    fn new(item: T) -> Counter<T> {
        Counter { item: item }
    }
}

/// Trait
trait ShowItem {
    fn show(&self) -> String;
}

/// Trait implementation
impl ShowItem for Count {
    fn show(&self) -> String {
        match *self {
            Count::One => "One!".to_string(),
            Count::Two(i) => format!("Two: {}!", i),
            Count::Three(ref s) => format!("Three: {}!", s),
            Count::Four { id: i, name: ref s } => format!("Four: {}, {}!", i, s),
        }
    }
}

// fn main()
fn main() {
    let mut c: Vec<Counter<Count>> = Vec::with_capacity(4);
    let c1 = Counter::new(Count::One);
    c.push(c1);
    let c2 = Counter::new(Count::Two(2));
    c.push(c2);
    let c3 = Counter::new(Count::Three("three".to_string()));
    c.push(c3);
    let c4 = Counter::new(Count::Four { id: 4, name: "four".to_string() });
    c.push(c4);

    let r: Vec<String> = c.into_iter().map(|i| i.item.show()).collect();

    for s in r.into_iter() {
        println!("-> {}", s);
    }
}
