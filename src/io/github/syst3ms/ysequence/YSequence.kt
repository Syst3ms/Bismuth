package io.github.syst3ms.ysequence

fun main() {
    val seq = arrayOf(1,2,4,5,4)
    val times = 4
    var valueMat : Array<Array<Int>> = arrayOf(seq)
    var offsetMat : Array<Array<Int>> = arrayOf(Array(seq.size) { 0 })

    // Calculate differences and offsets
    var row : Int
    while (valueMat.getColumn(valueMat.width - 1).last() > 1) {
        row = valueMat.size - 1
        valueMat = valueMat.resize(valueMat.width, valueMat.size + 1)
        offsetMat = offsetMat.resize(offsetMat.width, offsetMat.size + 1)
        for (i in 0 until valueMat.width) {
            val rowList = valueMat[row]
            if (rowList[i] <= 1) {
                valueMat[row + 1][i] = 0
                offsetMat[row][i] = 0
            } else {
                val parentIndex = rowList.copyOfRange(0, i)
                        .filterIndexed { j, _ -> !(row > 0 && j < i && j > i - offsetMat[row - 1][i])}
                        .indexOfLast { it < rowList[i]}
                valueMat[row + 1][i] = rowList[i] - rowList[parentIndex]
                offsetMat[row][i] = i - parentIndex
            }
        }
    }

    // Find bad root
    val badRoot = valueMat.width - offsetMat[valueMat.size - 2][valueMat.width - 1] - 1

    // Copy offsets
    val copyWidth = (offsetMat.width - badRoot - 1)
    var newWidth = badRoot + copyWidth * times + 1
    offsetMat = offsetMat.resize(newWidth, offsetMat.size)
    for (i in 1 until times) {
        for (j in 0 until copyWidth) {
            for (k in 0 until offsetMat.size) {
                offsetMat[k][badRoot + j + copyWidth * i + 1] = offsetMat[k][badRoot + j + 1]
            }
        }
    }
    offsetMat = offsetMat.resize(offsetMat.width - 1, offsetMat.size)

    // Copy bad part
    for (i in valueMat.indices) {
        valueMat[i][valueMat.width - 1] = 0
    }
    val badPartWidth = (valueMat.width- badRoot - 1)
    newWidth = badRoot + badPartWidth * times
    valueMat = valueMat.resize(newWidth, valueMat.size)
    val noCopy = valueMat.getColumn(badRoot).indexOfLast { it > 0 }
    for (i in 1 until times) {
        for (j in 0 until badPartWidth) {
            for (k in 0 until valueMat.size) {
                if (offsetMat[k][badRoot + j] == 0 && !(j == 0 && k == noCopy)) {
                    valueMat[k][badRoot + j + badPartWidth * i] = valueMat[k][badRoot + j]
                }
            }
        }
    }

    // Refill matrix
    val startColumn = valueMat[0].indexOf(0)
    for (i in (valueMat.size - 2) downTo 0) {
        for (j in startColumn until valueMat.width) {
            valueMat[i][j] = valueMat[i+1][j] + valueMat[i][j - offsetMat[i][j]]
        }
    }

    println(valueMat[0].joinToString(",", "(", ")"))
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