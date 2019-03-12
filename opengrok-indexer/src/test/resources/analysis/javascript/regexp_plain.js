function escapeLuceneCharacters1(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = /([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/;

    return term.replace(pattern, "\\$1");
}

function escapeLuceneCharacters2(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = {
        pattern: /([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/
    };

    return term.replace(pattern, "\\$1");
}

function escapeLuceneCharacters3(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = new RegExp(/([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/);

    return term.replace(pattern, "\\$1");
}
