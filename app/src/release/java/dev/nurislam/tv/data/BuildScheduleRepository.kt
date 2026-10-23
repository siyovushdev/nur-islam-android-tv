package dev.nurislam.tv.data

// No mock class, times or calendar are packaged into release.
fun buildScheduleRepository(): ScheduleRepository = UnavailableScheduleRepository()
