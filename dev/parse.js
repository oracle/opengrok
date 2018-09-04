var drafter = require('drafter');
var fs = require('fs');

new Promise(function(resolve, reject) {
        fs.readFile('apiary.apib', 'utf8', (error, data) => {
            if (error) {
                console.error(error);
                reject(1);
            }

            resolve(data);
        });
    })
    .then(function(data) {
        return new Promise((resolve, reject) => {
            drafter.parse(data, function(error, result) {
                if (error) {
                    console.log(error);
                    resolve(1);
                    return;
                } else {
                    for (x in result.content) {
                        var d = result.content[x];
                        // console.log(d);
                        if (d['element'] != 'category') {
                            console.log('Error:\n', d);
                            resolve(1);
                            return;
                        }
                    }
                }

                console.log("Blueprint check passed");
                resolve(0);
            });
        });
    })
    .then(process.exit)
    .catch(process.exit);