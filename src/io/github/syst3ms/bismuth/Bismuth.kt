@file:JvmName("Bismuth")
package io.github.syst3ms.bismuth

import kotlin.system.exitProcess

typealias Matrix = Array<Array<Int>>

fun main() {
    println("Please input a sequence of the form (1,a,...,z)[n] :")
    val input = readLine() ?: run {
        println("Invalid input")
        exitProcess(1)
    }
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
        val result = expand(seq, times + 1)
        println(result.valueMat[0].joinToString(",", "(", ")") + "[$times]")
    }
}

fun expand(seq: Array<Int>, times: Int, firstOffset: Array<Int>? = null): ExpansionResult {
    var valueMat: Matrix = arrayOf(seq)
    var offsetMat: Matrix = arrayOf(Array(seq.size) { 0 })

    // Calculate differences and offsets
    var row = 0
    while (findParentIndex(valueMat.last(), offsetMat.last(), if (row == 0) null else offsetMat.badRoot) != null) {
        valueMat = valueMat.resize(valueMat.width, valueMat.size + 1)
        offsetMat = offsetMat.resize(offsetMat.width, offsetMat.size + 1)
        for (i in 0 until valueMat.width) {
            val rowList = valueMat[row]
            if (firstOffset != null && row == 0) {
                val diff = rowList[i] - rowList[i - firstOffset[i]]
                valueMat[row + 1][i] = diff
                offsetMat[row][i] = firstOffset[i]
                continue
            }
            val parentIndex = findParentIndex(rowList.copyOfRange(0, i + 1), offsetMat[row].copyOfRange(0, i + 1), if (row > 0) i - offsetMat[row - 1][i].coerceAtLeast(0) else null)
            if (parentIndex == null) {
                valueMat[row + 1][i] = 0
                offsetMat[row][i] = if (rowList[i] == 0 || rowList.copyOfRange(0, i).all { it == 0 }) 0 else -rowList[i]
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
        val roots = (0 until valueMat.width).map { i -> valueMat.map { it[i] } } // Convert to columns
                .mapIndexed { i, c -> i to c.indexOfLast { it > 0 } } // Extract diagonal positions
                .toMutableList()
        val diagonal = roots.map { valueMat[it.second][it.first] }
        val shape = roots.zipWithNext { f, s -> s.second - f.second }
        // Calculate the diagonal's offsets
        val forcedOffset = Array(diagonal.size) { 0 }
        diag@for (i in roots.indices) {
            var c = roots[i]
            do {
                if (c == 0 to 0) {
                    forcedOffset[i] = if (i == 0) 0 else -diagonal[i]
                    continue@diag
                }
                c -= if (valueMat.getOrNull(c.second + 1)?.get(c.first) ?: 0 > 0) {
                    offsetMat[c.second][c.first] to 0
                } else {
                    offsetMat[c.second - 1][c.first] to 1 // go down-left when there is no actual parent
                }
            } while (valueMat[c.second + 1][c.first] > 0 || valueMat[c.second][c.first] >= diagonal[i])
            val rootIndex = roots.indexOf(c)
            forcedOffset[i] = i - rootIndex
        }
        roots.removeAt(roots.lastIndex)

        // Calculate the new diagonal
        val expandedDiagonal = expand(diagonal.toTypedArray(), times + 1, forcedOffset)
        val newShape = shape.copy(expandedDiagonal.badRoot, times)
        val newRoots = roots.copy(expandedDiagonal.badRoot, times)
        val newBounds = newShape.size to newShape.sum()
        val newDiagonal = expandedDiagonal.valueMat.first()

        // Copy offsets and values for the new diagonal
        row = 0
        var tempValue = valueMat.resize(newBounds.first + 1, newBounds.second + 1)
        var tempOffset = offsetMat.resize(newBounds.first + 1, newBounds.second + 1)
        val (badX, badY) = roots[expandedDiagonal.badRoot]
        val badPartOffset = offsetMat.slice(badX, badY, offsetMat.width - 1, badY).copyOffset(0, times)
        for (i in newShape.indices) {
            val s = newShape[i]
            val r = newRoots[i]
            val valuePart = valueMat.slice(r.first, minOf(r.second, r.second + s), r.first + 1, maxOf(r.second, r.second + s))
            valuePart[-s.coerceAtMost(0)][0] = newDiagonal[i]
            tempValue = tempValue.paste(valuePart, i, row + s.coerceAtMost(0))
            var offsetPart = offsetMat.slice(r.first, r.second, offsetMat.width - 1, r.second + s.coerceAtLeast(0))
            if (row >= badY && s > 0) {
                offsetPart = offsetPart.resize(tempOffset.width - i, offsetPart.size)
                        .paste(badPartOffset.slice(1, 0, badPartOffset.width - 1, 0), offsetPart.width, 0)
            } else if (s > 0) {
                offsetPart = offsetPart.copyOffset(badX - i, times)
            }
            tempOffset = tempOffset.paste(offsetPart, i, row)
            row += s
        }
        tempValue[row][newShape.size] = newDiagonal[newShape.size]
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
        for (i in 1 until times) {
            for (j in 0 until badPartWidth) {
                for (k in valueMat.indices) {
                    val x = badRoot + j
                    if (offsetMat[k][x] == 0 && !(k > 0 && x - offsetMat[k - 1][x] < badRoot)) {
                        valueMat[k][x + badPartWidth * i] = valueMat[k][x]
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

private fun findParentIndex(seq: Array<Int>, currentOffset: Array<Int>, badRoot: Int?): Int? {
    var i = badRoot ?: (seq.lastIndex - 1)
    val current = seq.last()
    while (i >= 0) {
        if (seq[i] in 1 until current) {
            return i
        } else if (seq[i] == 0) {
            break
        }
        i -= currentOffset[i].coerceAtLeast(1)
    }
    return null
}

private fun refillMatrix(valueMat: Matrix, offsetMat: Matrix): Matrix {
    for (i in (valueMat.lastIndex - 1) downTo 0) {
        for (j in 0 until valueMat.width) {
            val offset = offsetMat[i][j]
            valueMat[i][j] = if (offset < 0) -offset else (valueMat.getOrNull(i + 1)?.get(j) ?: 0) + valueMat[i][j - offset]
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
                val toCopy = result[k][badRoot + j + 1]
                result[k][target] = toCopy + (if (j - toCopy + 1 < 0) copyWidth * i else 0)
            }
        }
        i++
    }
    return result
}

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