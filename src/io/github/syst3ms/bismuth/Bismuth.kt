package io.github.syst3ms.bismuth

import kotlin.system.exitProcess

typealias Matrix = Array<Array<Int>>

fun main() {
    //println("Please input a sequence of the form (1,a,...,z)[n] :")
    val input = "(1,3,3)[4]"
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
    var valueMat: Matrix = arrayOf(seq)
    var offsetMat: Matrix = arrayOf(Array(seq.size) { 0 })

    // Calculate differences and offsets
    var row = 0
    while (findParentIndex(valueMat.last(), if (row == 0) null else offsetMat.badRoot) != -1) {
        valueMat = valueMat.resize(valueMat.width, valueMat.size + 1)
        offsetMat = offsetMat.resize(offsetMat.width, offsetMat.size + 1)
        for (i in 0 until valueMat.width) {
            val rowList = valueMat[row]
            val parentIndex = findParentIndex(rowList.copyOfRange(0, i + 1), if (row > 0) i - offsetMat[row - 1][i] else null)
            if (rowList[i] <= 1 || parentIndex == -1) {
                valueMat[row + 1][i] = 0
                offsetMat[row][i] = 0
            } else {
                val diff = rowList[i] - rowList[parentIndex]
                valueMat[row + 1][i] = diff
                offsetMat[row][i] = i - parentIndex
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
            coords += if (valueMat.getOrNull(coords.second + 1)?.getOrNull(coords.first + 1) ?: 0 > 0) {
                val root = offsetMat[coords.second].mapIndexed { i, e -> i - e }
                        .indexOfLast { it == coords.first } - coords.first
                shape.add(root to 1)
                root to 1
            } else {
                shape.add(1 to 0)
                1 to 0
            }
        }
        shape.removeAt(shape.lastIndex)

        // Calculate the new diagonal
        val roots = shape.mapIndexed { i, _ -> shape.subList(0, i).fold(0 to 0, Pair<Int, Int>::plus) }
        val expandedDiagonal = expand(diagonal.toTypedArray(), times + 1)
        val newShape = shape.copy(expandedDiagonal.badRoot, times)
        val newRoots = roots.copy(expandedDiagonal.badRoot, times)
        val newBounds = newShape.unzip()
                .run { first.sum() to second.sum() }
        val newDiagonal = expandedDiagonal.valueMat.first()

        // Copy offsets and values for the new diagonal
        coords = 0 to 0
        var tempValue = valueMat.resize(newBounds.first + 1, newBounds.second + 1)
        var tempOffset = offsetMat.resize(newBounds.first + 1, newBounds.second + 1)
        for (i in newShape.indices) {
            val s = newShape[i]
            val r = newRoots[i]
            val valuePart = valueMat.slice(r.first, r.second, r.first + s.first, r.second + s.second)
            valuePart[0][0] = newDiagonal[i]
            tempValue = tempValue.paste(valuePart, coords.first, coords.second)
            var offsetPart = offsetMat.slice(r.first, r.second, r.first + s.first, r.second + s.second)
            if (s.second > 0) {
                offsetPart = offsetPart.copyOffset(offsetPart.badRoot, size = tempOffset.width)
            }
            tempOffset = tempOffset.paste(offsetPart, coords.first, coords.second)
            coords += s
        }
        tempValue[coords.second][coords.first] = newDiagonal[newShape.size]
        return ExpansionResult(refillMatrix(tempValue, tempOffset), tempOffset, badRoot)
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

val Matrix.badRoot: Int
    get() = width - this[size - 2][width - 1] - 1

private fun Matrix.paste(part: Matrix, x: Int, y: Int): Matrix {
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

private fun refillMatrix(valueMat: Matrix, offsetMat: Matrix): Matrix {
    for (i in (valueMat.size - 1) downTo 0) {
        for (j in 0 until valueMat.width) {
            valueMat[i][j] = (valueMat.getOrNull(i + 1)?.get(j) ?: 0) + valueMat[i][j - offsetMat[i][j]]
        }
    }
    return valueMat
}

private fun Matrix.copyOffset(badRoot: Int, times: Int? = null, size: Int? = null): Matrix {
    assert(times != null || size != null)
    var result = this
    val copyWidth = (result.width - badRoot - 1)
    val newWidth = if (times != null) badRoot + copyWidth * times + 1 else size!!
    result = result.resize(newWidth, result.size)
    var i = 1
    while (result.first().last() == 0) {
        for (j in 0 until copyWidth) {
            val target = badRoot + copyWidth * i + j + 1
            if (target >= newWidth)
                break
            for (k in result.indices) {
                result[k][target] = result[k][badRoot + j + 1]
            }
        }
        i++
    }
    return result
}

fun Matrix.getColumn(i: Int) = this.map { it[i] }.toTypedArray()

fun <T : Any> List<T>.copy(root: Int, times: Int): List<T> {
    val new = this.subList(0, root).toMutableList()
    repeat(times) {
        new += this.subList(root, this.size)
    }
    return new
}

val Matrix.width: Int
    get() = this[0].size

fun Matrix.resize(newWidth: Int, newHeight: Int): Matrix {
    var result = this
    val height = size
    val width = this[0].size
    if (height < newHeight) {
        result = result.plus(Matrix(newHeight - height) { Array(width) { 0 } }.toList())
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

fun Matrix.slice(x: Int, y: Int, x1 : Int, y1: Int) = this.copyOfRange(y, y1 + 1)
        .map { it.copyOfRange(x, x1 + 1) }
        .toTypedArray()

data class ExpansionResult(val valueMat: Matrix, val offsetMat: Matrix, val badRoot: Int)

operator fun Pair<Int, Int>.plus(o: Pair<Int, Int>) = (first + o.first) to (second + o.second)

operator fun Pair<Int, Int>.minus(o: Pair<Int, Int>) = (first - o.first) to (second - o.second)