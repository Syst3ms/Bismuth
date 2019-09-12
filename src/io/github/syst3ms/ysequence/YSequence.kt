package io.github.syst3ms.ysequence

class YMatrix(seq: Array<Int>) {
    private val valueMap : MutableList<MutableList<Int>> = mutableListOf(mutableListOf())
    private val offsetMap : MutableList<MutableList<Int>> = mutableListOf(mutableListOf())

    var width : Int = seq.size
        private set
    var height: Int = 1
        private set

    init {
        valueMap[0].addAll(seq)
    }

    fun getValue(x: Int, y: Int) = valueMap.getOrNull(y)?.getOrNull(x) ?: 0

    fun getOffset(x: Int, y: Int) = offsetMap.getOrNull(y)?.getOrNull(x) ?: 0

    fun getColumn(c: Int) = valueMap.map { it[c] }

    private fun calculateDifferences() {
        var row : Int
        while (getColumn(width - 1).last() > 1) {
            row = height - 1
            extend(height + 1, width)
            for (i in 0 until width) {
                val rowList = valueMap[row]
                if (rowList[i] <= 1) {
                    valueMap[row + 1][i] = 0
                    offsetMap[row][i] = 0
                } else {
                    val parentIndex = rowList.subList(0, i).indexOfLast { it < rowList[i]}
                    valueMap[row + 1][i] = rowList[i] - rowList[parentIndex]
                    offsetMap[row][i] = i - parentIndex
                }
            }
        }
    }

    private fun copyBadPart(badRoot: Int, times: Int) {
        clearColumn(width - 1)
        val badPartWidth = (width - badRoot - 1)
        val newWidth = badRoot + badPartWidth * times
        extend(newWidth, height)
        val noCopy = getColumn(badRoot).indexOfLast { it > 0 }
        for (i in 1 until times) {
            for (j in 0 until badPartWidth) {
                for (k in 0 until height) {
                    if (getOffset(j, k) == 0 && !(j == 0 && k == noCopy)) {
                        valueMap[k][j + badPartWidth * i] = valueMap[k][j]
                    }
                }
            }
        }
    }

    private fun clearColumn(c: Int) {
        for (i in 0 until width) {
            valueMap[i][c] = 0
            offsetMap[i][c] = 0
        }
    }

    private fun extend(width: Int, height: Int) {
        if (this.height < height) {
            repeat(height - this.height) {
                valueMap.add(Array(width) { 0 }.toMutableList())
                offsetMap.add(Array(width) { 0 }.toMutableList())
            }
            this.height = height
        }
        if (this.width < width) {
            for (i in 0 until height) {
                repeat(width - this.width) {
                    valueMap[i].add(0)
                    offsetMap[i].add(0)
                }
            }
            this.width = width
        }
    }
}