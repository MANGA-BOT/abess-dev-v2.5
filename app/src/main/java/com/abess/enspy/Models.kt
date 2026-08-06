package com.abess.enspy

import org.json.JSONArray
import org.json.JSONObject

data class Student(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val level: String = "",
    val filiere: String = ""
)

data class Document(
    val id: Int,
    val title: String,
    val description: String,
    val fileUrl: String,
    val docType: String,
    val subject: String,
    val level: String,
    val filiere: String
)

data class ForumPost(
    val id: Int,
    val title: String,
    val content: String,
    val author: String,
    val subject: String,
    val votes: Int,
    val answers: Int
)

data class CalendarEvent(
    val id: Int,
    val title: String,
    val description: String,
    val eventType: String,
    val startsAt: String,
    val location: String
)

fun JSONObject.asStudent(): Student = Student(
    id = optInt("id"),
    name = optString("name", optString("fullName")),
    email = optString("email"),
    level = optString("levelName", optString("level")),
    filiere = optString("filiereName", optString("filiere"))
)

fun JSONObject.asDocument(): Document = Document(
    id = optInt("id"),
    title = optString("title", "Document"),
    description = optString("description"),
    fileUrl = optString("fileUrl", optString("file_url")),
    docType = optString("docType", optString("type", "cours")),
    subject = optString("subjectName", optString("subject")),
    level = optString("levelName", optString("level")),
    filiere = optString("filiereName", optString("filiere"))
)

fun JSONObject.asForumPost(): ForumPost = ForumPost(
    id = optInt("id"),
    title = optString("title", "Question"),
    content = optString("content"),
    author = optString("authorName", optString("author", "Étudiant")),
    subject = optString("subjectName", optString("subject")),
    votes = optInt("votes", optInt("voteCount")),
    answers = optInt("answersCount", optInt("answers"))
)

fun JSONObject.asCalendarEvent(): CalendarEvent = CalendarEvent(
    id = optInt("id"),
    title = optString("title", "Événement"),
    description = optString("description"),
    eventType = optString("eventType", "special"),
    startsAt = optString("startsAt", optString("startAt", optString("date"))),
    location = optString("location")
)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.dataArray(): JSONArray? =
    when {
        has("data") && opt("data") is JSONArray -> optJSONArray("data")
        has("items") && opt("items") is JSONArray -> optJSONArray("items")
        else -> null
    }