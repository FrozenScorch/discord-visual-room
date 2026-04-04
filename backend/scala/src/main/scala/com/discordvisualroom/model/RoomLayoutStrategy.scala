package com.discordvisualroom.model

object RoomLayoutStrategy {
  val ROOM_SIZE: Double = 24.0
  val CORRIDOR_WIDTH: Double = 6.0
  val ROOM_CELL: Double = ROOM_SIZE + CORRIDOR_WIDTH

  def computePositions(channels: Seq[(String, Int)]): Map[String, RoomPosition] = {
    // channels: Seq[(id, sortPosition)]
    val sorted = channels.sortBy(_._2)
    val n = sorted.size
    if (n == 0) return Map.empty

    val cols = math.ceil(math.sqrt(n.toDouble)).toInt
    val rows = math.ceil(n.toDouble / cols).toInt
    val totalWidth = cols * ROOM_CELL
    val totalDepth = rows * ROOM_CELL

    sorted.zipWithIndex.map { case ((id, _), index) =>
      val col = index % cols
      val row = index / cols
      val x = col * ROOM_CELL + ROOM_SIZE / 2.0 - totalWidth / 2.0 + ROOM_CELL / 2.0
      val z = row * ROOM_CELL + ROOM_SIZE / 2.0 - totalDepth / 2.0 + ROOM_CELL / 2.0
      id -> RoomPosition(x, z)
    }.toMap
  }

  /**
   * Compute positions for text channels, placed in a separate grid section below voice channels.
   */
  def computeTextChannelPositions(
    textChannels: Seq[(String, Int)],
    voiceChannelCount: Int
  ): Map[String, RoomPosition] = {
    val sorted = textChannels.sortBy(_._2)
    val n = sorted.size
    if (n == 0) return Map.empty

    val cols = math.ceil(math.sqrt(n.toDouble)).toInt
    val voiceRows = math.ceil(math.sqrt(voiceChannelCount.toDouble)).toInt
    val voiceGridDepth = voiceRows * ROOM_CELL
    val textOffsetZ = voiceGridDepth / 2.0 + ROOM_SIZE + CORRIDOR_WIDTH * 2.0

    sorted.zipWithIndex.map { case ((id, _), index) =>
      val col = index % cols
      val row = index / cols
      val totalWidth = cols * ROOM_CELL
      val x = col * ROOM_CELL + ROOM_SIZE / 2.0 - totalWidth / 2.0 + ROOM_CELL / 2.0
      val z = textOffsetZ + row * ROOM_CELL + ROOM_SIZE / 2.0
      id -> RoomPosition(x, z)
    }.toMap
  }
}
