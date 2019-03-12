function escapeLuceneCharacters1(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = /([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/gms;

    return term.replace(pattern, "\\$1");
}

function escapeLuceneCharacters2(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = {
        pattern: /([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/gms
    };

    return term.replace(pattern, "\\$1");
}

function escapeLuceneCharacters3(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    var pattern = new RegExp(/([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/gms);

    return term.replace(pattern, "\\$1");
}
