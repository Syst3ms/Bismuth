package io.github.syst3ms.ysequence

import kotlin.system.exitProcess

fun main() {
    println("Please input a sequence of the form (1,a,...,z)[n] :")
    val input = "(1,3,3)[5]"
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

fun expand(seq: Array<Int>, times: Int): ExpansionResult {
    var valueMat: Array<Array<Int>> = arrayOf(seq)
    var offsetMat: Array<Array<Int>> = arrayOf(Array(seq.size) { 0 })

    // Calculate differences and offsets
    var row = 0
    while (findParentIndex(valueMat.last(), if (row == 0) null else offsetMat.badRoot) != -1) {
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
                }
            }
        }
        row++
    }

    // Find bad root
    val badRoot = offsetMat.badRoot

    // Check for diagonal expansion
    val lastRow = valueMat.last()
    if (lastRow.last() > 1) {

        // Calculate diagonal shape & offsets
        val shape = mutableListOf<Pair<Int, Int>>()
        val diagonal = mutableListOf<Int>()
        var coords = 0 to 0
        while (coords.first < valueMat.width) {
            diagonal.add(valueMat[coords.second][coords.first])
            val root = offsetMat[coords.second].last()
            if (valueMat.getOrNull(coords.second + 1)?.getOrNull(coords.first + 1) ?: 0 > 0) {
                if (coords.first == offsetMat.width - root - 1) {
                    shape.add(root to 1)
                    coords += root - 1 to 0
                } else {
                    shape.add(1 to 1)
                }
                coords += 0 to 1
            } else {
                shape.add(1 to 0)
            }
            coords += 1 to 0
        }
        shape.removeAt(shape.lastIndex)

        // Compute diagonal's expansion
        val expandedDiagonal = expand(diagonal.toTypedArray(), times + 1)

        // Compute new diagonal shape
        val shapeGoodPart = shape.subList(0, expandedDiagonal.badRoot)
        val expandedShape = shapeGoodPart + Array(times) { shape.drop(expandedDiagonal.badRoot) }.toList().flatten()
        val diagonalRoots = expandedShape.map { it - expandedShape.first() }
        val diagonalOffsets = expandedShape.mapIndexed { i, _ -> expandedShape.subList(0, i + 1).reduce(Pair<Int, Int>::plus) }
        val bounds = diagonalOffsets.last() - diagonalRoots.last()

        // Fill in new diagonal with some values and offsets

        val newDiagonal = expandedDiagonal.valueMat.first()
        valueMat = valueMat.resize(bounds.first + 1, bounds.second + 1)
        val offsetTimes = times * shape.map { it.first }.sum()
        offsetMat = offsetMat.copyOffset(badRoot, offsetTimes)
                .resize(valueMat.width, valueMat.size)
        for (i in diagonalOffsets.indices) {
            val o = diagonalOffsets[i]
            val r = diagonalRoots[i]
            val valuePart = valueMat.copyOfRange(r.second, r.second + o.second + 1)
                    .map { it.copyOfRange(r.first, r.first + o.first + 1) }
                    .toTypedArray()
            valuePart[0][0] = newDiagonal[i]
            valueMat = valueMat.pasteMatrix(valuePart, o.first - r.first, o.second - r.second)
            val offsetPart = offsetMat.copyOfRange(r.second, r.second + o.second + 1)
                    .map { it.copyOfRange(r.first, r.first + o.first + 1) }
                    .toTypedArray()
            offsetMat = offsetMat.pasteMatrix(
                    offsetPart.copyOffset(if (offsetPart.size > 1) offsetPart.badRoot else 0, offsetTimes),
                    o.first - r.first,
                    o.second - r.second
            )
        }

        return ExpansionResult(refillMatrix(valueMat, offsetMat), offsetMat, badRoot)
    } else {

        // Copy offsets
        offsetMat = offsetMat.copyOffset(badRoot, times)

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

val Array<Array<Int>>.badRoot: Int
    get() = width - this[size - 2][width - 1] - 1

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

private fun Array<Array<Int>>.copyOffset(badRoot: Int, times: Int): Array<Array<Int>> {
    var result = this
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

operator fun Pair<Int, Int>.plus(o: Pair<Int, Int>) = (first + o.first) to (second + o.second)

operator fun Pair<Int, Int>.minus(o: Pair<Int, Int>) = (first - o.first) to (second - o.second)