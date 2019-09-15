package io.github.syst3ms.ysequence

import kotlin.system.exitProcess

fun main() {
    println("Please input a sequence of the form (1,a,...,z)[n] :")
    val input = "(1,3,4,7,11,18)[5]"
    val match = """\((\d+(?:,\d+)*)\)\[(\d+)]""".toRegex().matchEntire(input)
    if (match == null) {
        println("Invalid input")
        exitProcess(1)
    }
    val seq = match.groupValues[1]
            .split(',')
            .map { it.toInt() }
            .toTypedArray()
    val times = match.groupValues[2].toInt()
    if (seq.last() == 1) {
        println(seq.dropLast(1).joinToString(",", "(", ")") + "[${times + 1}]")
    } else {
        val result = expand(seq, times)
        println(result.valueMat[0].joinToString(",", "(", ")") + "[$times]")
    }
}

fun expand(seq: Array<Int>, times: Int, forceOffset: Int? = null): ExpansionResult {
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
                val parentIndex = findParentIndex(rowList.copyOfRange(0, i + 1), if (row > 0) i - offsetMat[row - 1][i] else null)
                if (parentIndex == -1) {
                    valueMat[row + 1][i] = 0
                    offsetMat[row][i] = 0
                } else {
                    val diff = rowList[i] - rowList[parentIndex]
                    valueMat[row + 1][i] = diff
                    offsetMat[row][i] = i - parentIndex
                    if (rowList[parentIndex] <= baseDifference || (parentIndex >= row && diff > 0 && diff < baseDifference)) {
                        baseDifference = diff
                    }
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
    val top = valueMat.getColumn(valueMat.width - 1).last()
    if (top > 1 &&
            baseDifference > 1 &&
            top <= baseDifference &&
            findParentIndex(lastRow, badRoot) == -1
    ) {

        // Calculate diagonal shape & offsets
        val shape = mutableListOf<Boolean>()
        val diagonal = mutableListOf<Int>()
        var x = 0
        var y = 0
        var diagonalX = 1
        var diagonalY = 1
        while (x < valueMat.width) {
            val element = valueMat[y][x]
            if (element == 1 && x == offsetMat.width - offsetMat[y].last() - 1) {
                diagonal.clear()
                shape.clear()
                badRoot = x
                diagonalX = x + 1
                diagonalY = y + 1
            }
            diagonal.add(element)
            if (valueMat.getOrNull(y + 1)?.getOrNull(x + 1) ?: 0 > 0) {
                shape.add(true)
                y++
            } else {
                shape.add(false)
            }
            x++
        }

        // Compute diagonal's expansion
        val expandedDiagonal = expand(diagonal.toTypedArray(), times + 1, if (lastRow.none { it > 0 && it < lastRow.last() }) null else offsetMat[y - 1][x - 1])
        var newDiagonal = expandedDiagonal.valueMat.first()

        val shapeOffset = shape.dropLast(1)
                .mapIndexed { i, e -> if (i < expandedDiagonal.badRoot) 0 to 0 else if (e) 1 to 1 else 0 to 1 }
                .fold(0 to 0) { a, e -> (a.first + e.first) to (a.second + e.second) }

        newDiagonal = newDiagonal.copyOfRange(0, minOf(shapeOffset.second * times + 1, newDiagonal.size))
        x = diagonalX
        y = diagonalY
        val newWidth = x + shapeOffset.second * times
        val newHeight = y + shapeOffset.first * times
        valueMat = valueMat.resize(newWidth, newHeight)
        for (i in 1 until newDiagonal.size) {
            valueMat[y][x] = newDiagonal[i]
            if (shape.dropLast(1)[i % (shape.size - 1)]) {
                y++
            }
            x++
        }
        // Copy offsets
        var tempOffset = copyOffset(offsetMat, badRoot, times * shapeOffset.second + 1)
                .resize(valueMat.width, valueMat.size)
        if (diagonalX == 1 && diagonalY == 1) {
            for (i in 1 until times) {
                val offsetPart = copyOffset(offsetMat, badRoot, (times - i + 1)  * shapeOffset.second)
                tempOffset = tempOffset.pasteMatrix(offsetPart, shapeOffset.second * i, shapeOffset.first * i)
            }
        } else {
            val subOffset = expand(valueMat[diagonalY - 1]
                    .copyOfRange(diagonalX - 1, valueMat.width)
                    .dropLastWhile { it == 0 }
                    .toTypedArray(), times).offsetMat
            tempOffset = tempOffset.pasteMatrix(subOffset, diagonalX - 1, diagonalY - 1)
        }
        offsetMat = tempOffset

        return ExpansionResult(refillMatrix(valueMat, offsetMat), offsetMat, badRoot)
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
                for (k in valueMat.indices) {
                    if (offsetMat[k][badRoot + j] == 0 && !(j == 0 && k == noCopy)) {
                        valueMat[k][badRoot + j + badPartWidth * i] = valueMat[k][badRoot + j]
                    }
                }
            }
        }

        return ExpansionResult(refillMatrix(valueMat, offsetMat), offsetMat, badRoot)
    }
}

private fun Array<Array<Int>>.pasteMatrix(part: Array<Array<Int>>, x: Int, y: Int): Array<Array<Int>> {
    for (i in part.indices) {
        if (y + i >= this.size)
            break
        for (j in 0 until part.width) {
            if (x + j >= this.width)
                break
            this[y + i][x + j] = part[i][j]
        }
    }
    return this
}

private fun findParentIndex(seq: Array<Int>, badRoot: Int?): Int {
    for (i in (seq.lastIndex - 1) downTo 0) {
        if (seq[i] == 0 || seq[i] >= seq.last() || badRoot != null && i > badRoot) {
            continue
        } else {
            return i
        }
    }
    return -1
}

private fun refillMatrix(valueMat: Array<Array<Int>>, offsetMat: Array<Array<Int>>): Array<Array<Int>> {
    for (i in (valueMat.size - 1) downTo 0) {
        for (j in 0 until valueMat.width) {
            valueMat[i][j] = (valueMat.getOrNull(i + 1)?.get(j) ?: 0) + valueMat[i][j - offsetMat[i][j]]
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
            for (k in result.indices) {
                result[k][badRoot + j + copyWidth * i + 1] = result[k][badRoot + j + 1]
            }
        }
    }
    return result.resize(result.width - 1, result.size)
}

fun Array<Array<Int>>.getColumn(i: Int) = this.map { it[i] }.toTypedArray()

val Array<Array<Int>>.width: Int
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
        result = result.map { it.plus(Array(newWidth - width) { 0 }.toList()) }.toTypedArray()
    } else if (newWidth < width) {
        result = result.map { it.dropLast(width - newWidth).toTypedArray() }.toTypedArray()
    }
    return result
}

data class ExpansionResult(val valueMat: Array<Array<Int>>, val offsetMat: Array<Array<Int>>, val badRoot: Int)