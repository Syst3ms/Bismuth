package io.github.syst3ms.ysequence

fun main() {
    val seq = arrayOf(1,3,3)
    val times = 3
    val result = expand(seq, times)

    println(result[0].joinToString(",", "(", ")"))
}

fun expand(seq: Array<Int>, times: Int, forceOffset : Int? = null): Array<Array<Int>> {
    var valueMat: Array<Array<Int>> = arrayOf(seq)
    var offsetMat: Array<Array<Int>> = arrayOf(Array(seq.size) { 0 })

    // Calculate differences and offsets
    var row: Int
    var baseDifference = 1
    while (valueMat.getColumn(valueMat.width - 1).last() > baseDifference) {
        row = valueMat.size - 1
        valueMat = valueMat.resize(valueMat.width, valueMat.size + 1)
        offsetMat = offsetMat.resize(offsetMat.width, offsetMat.size + 1)
        for (i in 0 until valueMat.width) {
            val rowList = valueMat[row]
            if (rowList[i] <= 1) {
                valueMat[row + 1][i] = 0
                offsetMat[row][i] = 0
            } else {
                val parentIndex = rowList.copyOfRange(row, i)
                        .filterIndexed { j, _ -> !(row > 0 && j < i && j > i - offsetMat[row - 1][i]) }
                        .indexOfLast { it < rowList[i] }
                valueMat[row + 1][i] = rowList[i] - rowList[parentIndex]
                offsetMat[row][i] = i - parentIndex
                if (i == row + 1) {
                    baseDifference = valueMat[row + 1][row + 1]
                }
            }
        }
    }

    if (forceOffset != null) {
        offsetMat[0][offsetMat.width - 1] = forceOffset
    }

    // Find bad root
    var badRoot = valueMat.width - offsetMat[valueMat.size - 2][valueMat.width - 1] - 1

    // Check for diagonal expansion
    val lastRow = valueMat.last()
    if (baseDifference > 1 &&
            valueMat.getColumn(valueMat.width - 1).last() == baseDifference &&
            lastRow.filterIndexed { j, e -> !(e == 0 || e >= lastRow.last() || j == valueMat.width - 1 || j > badRoot) }
                    .isEmpty()
    ) {
        // New diagonal root
        badRoot = valueMat.size - 2

        // Calculate diagonal shape & offsets
        val shape = mutableListOf<Boolean>()
        val diagonal = mutableListOf<Int>()
        var x = 0
        var y = 0
        while (x < valueMat.width) {
            diagonal.add(valueMat[y][x])
            if (valueMat.getOrNull(y + 1)?.get(x + 1) ?: 0 > 0) {
                shape.add(true)
                y++
            } else {
                shape.add(false)
            }
            x++
        }
        val shapeOffset = shape.dropLast(1)
                .map { if (it) 1 to 1 else 0 to 1 }
                .fold(0 to 0) { a, e -> (a.first + e.first) to (a.second + e.second) }

        // Compute diagonal's expansion
        val expandedDiagonal = expand(diagonal.toTypedArray(), times + 1, if (lastRow.none { it > 0 && it < lastRow.last()}) null else offsetMat[y - 1][x - 1] )
                .first()
                .copyOfRange(0, shapeOffset.second * times + 1)
        x = 1
        y = 1
        val newWidth = 1 + shapeOffset.second * times
        val newHeight = 1 + shapeOffset.first * times
        valueMat = valueMat.resize(newWidth, newHeight)
        for (i in 1 until expandedDiagonal.size) {
            valueMat[y][x] = expandedDiagonal[i]
            if (shape.dropLast(1)[i % (shape.size - 1)]) {
                y++
            }
            x++
        }


        // Copy offsets
        val tempOffset = offsetMat.resize(valueMat.width, valueMat.size)
        for (i in 0 until times) {
            val offsetPart = copyOffset(offsetMat, badRoot, times - i)
            for (j in 0 until offsetPart.size) {
                for (k in 0 until offsetPart.width) {
                    tempOffset[j + shapeOffset.first * i][k + shapeOffset.second * i] = offsetPart[j][k]
                }
            }
        }
        offsetMat = tempOffset

        return refillMatrix(valueMat, offsetMat)
    } else {

        // Copy offsets
        offsetMat = copyOffset(offsetMat, badRoot, times)

        // Copy bad part
        for (i in valueMat.indices) {
            valueMat[i][valueMat.width - 1] = 0
        }
        val badPartWidth = (valueMat.width - badRoot - 1)
        val newWidth = badRoot + badPartWidth * times
        valueMat = valueMat.resize(newWidth, valueMat.size)
        val noCopy = valueMat.getColumn(badRoot).indexOfLast { it > 0 }
        for (i in 0 until times) {
            for (j in 0 until badPartWidth) {
                for (k in 0 until valueMat.size) {
                    if (offsetMat[k][badRoot + j] == 0 && !(j == 0 && k == noCopy)) {
                        valueMat[k][badRoot + j + badPartWidth * i] = valueMat[k][badRoot + j]
                    }
                }
            }
        }

        return refillMatrix(valueMat, offsetMat)
    }
}

private fun refillMatrix(valueMat: Array<Array<Int>>, offsetMat: Array<Array<Int>>): Array<Array<Int>> {
    for (i in (valueMat.size - 1) downTo 0) {
        for (j in 0 until valueMat.width) {
            valueMat[i][j] = (valueMat.getOrNull(i+1)?.get(j) ?: 0) + valueMat[i][j - offsetMat[i][j]]
        }
    }
    return valueMat
}

private fun copyOffset(offsetMat: Array<Array<Int>>, badRoot: Int, times: Int): Array<Array<Int>> {
    var result = offsetMat
    val copyWidth = (result.width - badRoot - 1)
    val newWidth = badRoot + copyWidth * times + 1
    result = result.resize(newWidth, result.size)
    for (i in 1 until times) {
        for (j in 0 until copyWidth) {
            for (k in 0 until result.size) {
                result[k][badRoot + j + copyWidth * i + 1] = result[k][badRoot + j + 1]
            }
        }
    }
    return result//.resize(result.width - 1, result.size)
}

fun Array<Array<Int>>.getColumn(i: Int) = this.map { it[i] }.toTypedArray()

val Array<Array<Int>>.width : Int
    get() = this[0].size

fun Array<Array<Int>>.resize(newWidth: Int, newHeight: Int): Array<Array<Int>> {
    var result = this
    val height = size
    val width = this[0].size
    if (height < newHeight) {
        result = result.plus(Array(newHeight - height) { Array(width) { 0 } }.toList())
    } else if (newHeight < height) {
        result = result.dropLast(height - newHeight).toTypedArray()
    }
    if (width < newWidth) {
        result = result.map { it.plus(Array(newWidth - width) {0}.toList()) }.toTypedArray()
    } else if (newWidth < width) {
        result = result.map { it.dropLast(width - newWidth).toTypedArray() }.toTypedArray()
    }
    return result
}
