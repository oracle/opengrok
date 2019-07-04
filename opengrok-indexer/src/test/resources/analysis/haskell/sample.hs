-- classic quick sort (short but not so efficient)
qsort [] = []
qsort (x:xs) = qsort [ x' | x' <- xs, x' < x ] ++ [x] ++ qsort [ x' | x' <- xs, x' >= x ]

-- weird but legal identifiers
x'y' = let f' = 1; g'h = 2 in f' + g'h
