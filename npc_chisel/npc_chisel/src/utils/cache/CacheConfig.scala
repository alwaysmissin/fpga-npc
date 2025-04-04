package utils.cache

import chisel3._
import chisel3.util.log2Up

abstract class CacheBasicConfig {
  val blocks: Int
  val blockSize: Int
  val ways: Int
  val offsetWidth = log2Up(blockSize)
  val wordOffsetWidth = offsetWidth - 2
  val indexWidth = log2Up(blocks)
  val tagWidth = 32 - offsetWidth - indexWidth
  val offsetRangeLo = 0
  val offsetRangeHi = offsetWidth - 1
  val wordOffsetRangeLo = 2
  val wordOffsetRangeHi = offsetWidth - 1
  val indexRangeLo = wordOffsetRangeHi + 1
  val indexRangeHi = indexRangeLo + indexWidth - 1
  val tagRangeLo = offsetWidth + indexWidth
  val tagRangeHi = 31
  val blockWords = blockSize >> 2
}

final case class ICacheConfig(
  blocks: Int = 16,
  blockSize: Int = 16,
  ways: Int = 2,
) extends CacheBasicConfig{
  assert(blocks >= 2)
}

