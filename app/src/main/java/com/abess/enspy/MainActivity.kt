package com.abess.enspy

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import org.json.JSONArray
import org.json.JSONObject
import com.abess.enspy.SecureStore
import com.abess.enspy.ApiClient
import com.abess.enspy.Student
import com.abess.enspy.asStudent // Si c'est une fonction d'extension


class MainActivity : AppCompatActivity() {
    private lateinit var store: SecureStore
    private lateinit var api: ApiClient
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private var student = Student()
    private var selectedTab = 0
    private val orange by lazy { ContextCompat.getColor(this, R.color.enspy_orange) }
    private val ink by lazy { ContextCompat.getColor(this, R.color.enspy_ink) }
    private val cream by lazy { ContextCompat.getColor(this, R.color.enspy_cream) }
    private val muted by lazy { ContextCompat.getColor(this, R.color.enspy_muted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = ContextCompat.getColor(this, R.color.enspy_orange_dark)
        store = SecureStore(this)
        api = ApiClient(store)
        val saved = store.get("student")
        if (saved != null) student = runCatching { JSONObject(saved).asStudent() }.getOrDefault(Student())
        if (store.get("token").isNullOrBlank()) showAuth() else showApp()
    }

    private fun showAuth() {
        val root = LinearLayout(this).vertical().apply {
            background = verticalGradient(cream, Color.WHITE)
            setPadding(dp(22), dp(18), dp(22), dp(26))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.enspy_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = rounded(Color.WHITE, dp(18), R.color.enspy_line)
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(124), dp(124)).apply {
                topMargin = dp(18)
                bottomMargin = dp(14)
            }
        }
        root.addView(logo)
        val brand = text("ENSPY", 34, orange, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        root.addView(brand)
        root.addView(text("La réussite académique, ensemble.", 15, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(24))
        })
        val card = LinearLayout(this).vertical().apply {
            setPadding(dp(22), dp(22), dp(22), dp(20))
            background = rounded(Color.WHITE, dp(22), R.color.enspy_line)
            elevation = dp(5).toFloat()
        }
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        card.addView(text("ESPACE ÉTUDIANT", 12, orange, true).apply {
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dp(8))
        })
        val mode = TextView(this)
        mode.text = "Connexion"
        mode.setTextSize(23f)
        mode.setTextColor(ink)
        mode.setTypeface(null, Typeface.BOLD)
        mode.includeFontPadding = true
        card.addView(mode)
        card.addView(text("Accède à tes cours, tes examens et la communauté ENSPY.", 14, muted).apply {
            setPadding(0, dp(6), 0, dp(18))
        })
        val name = authField("Nom complet", false)
        val email = authField("Adresse e-mail", false)
        val password = authField("Mot de passe", true)
        val level = authField("Niveau (1 ou 2)", false)
        val filiere = authField("Filière (INFO, MSP, ...)", false)
        card.addView(email)
        card.addView(password)
        card.addView(name)
        card.addView(level)
        card.addView(filiere)
        name.visibility = View.GONE
        level.visibility = View.GONE
        filiere.visibility = View.GONE
        val action = button("Se connecter", true)
        card.addView(action, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        val toggle = button("Créer un compte étudiant", false)
        card.addView(toggle, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        val help = text("Une question sur l’application ?", 13, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        }
        card.addView(help)
        val whatsapp = whatsappButton()
        card.addView(whatsapp, LinearLayout.LayoutParams(-1, dp(48)))
        whatsapp.setOnClickListener { contactDeveloper() }
        fun updateRegister(register: Boolean) {
            mode.text = if (register) "Créer mon compte" else "Connexion"
            action.text = if (register) "S'inscrire" else "Se connecter"
            toggle.text = if (register) "J'ai déjà un compte" else "Créer un compte étudiant"
            name.visibility = if (register) View.VISIBLE else View.GONE
            level.visibility = if (register) View.VISIBLE else View.GONE
            filiere.visibility = if (register) View.VISIBLE else View.GONE
        }
        var registerMode = false
        toggle.setOnClickListener { registerMode = !registerMode; updateRegister(registerMode) }
        action.setOnClickListener {
            if (email.text.toString().trim().isEmpty() || password.text.toString().isEmpty()) {
                toast("Renseigne ton e-mail et ton mot de passe."); return@setOnClickListener
            }
            action.isEnabled = false
            val body = JSONObject().apply {
                put("email", email.text.toString().trim())
                put("password", password.text.toString())
                if (registerMode) {
                    put("name", name.text.toString().trim())
                    put("levelId", level.text.toString().trim().toIntOrNull() ?: 1)
                    put("filiereId", filiere.text.toString().trim().toIntOrNull() ?: 1)
                }
            }
            api.post(if (registerMode) "/api/auth/register" else "/api/auth/login", body) { status, raw ->
                action.isEnabled = true
                val response = parseResponse(raw)
                val token = response.optString("token")
                if (status in 200..299 && token.isNotBlank()) {
                    store.put("token", token)
                    val user = response.optJSONObject("user") ?: response.optJSONObject("student")
                    if (user != null) {
                        student = user.asStudent()
                        store.put("student", user.toString())
                    }
                    showApp()
                } else toast(response.optString("message", if (raw.isBlank()) "Connexion impossible." else raw.take(160)))
            }
        }
        setContentView(scroll)
    }

    private fun showApp() {
        val root = LinearLayout(this).vertical().apply { setBackgroundColor(cream) }
        val top = LinearLayout(this).apply {
            setPadding(20, 18, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(orange)
        }
        title = text("Accueil", 22, Color.WHITE, true)
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val notification = TextView(this).apply {
            text = "♢"
            setTextSize(30f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener { showNotifications() }
        }
        top.addView(notification, LinearLayout.LayoutParams(48, 48))
        root.addView(top)
        content = LinearLayout(this).vertical().apply {
            setPadding(18, 16, 18, 18)
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNav())
        setContentView(root)
        showTab(0)
    }

    private fun bottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            setPadding(4, 6, 4, 6)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val labels = listOf("Accueil", "Cours", "Forum", "Agenda", "Profil")
        labels.forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = "${listOf("⌂", "▤", "◇", "▦", "○")[index]}\n$label"
                setTextSize(11f)
                gravity = Gravity.CENTER
                setTextColor(if (index == selectedTab) orange else muted)
                setTypeface(null, if (index == selectedTab) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener { showTab(index) }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, 58, 1f))
        }
        return nav
    }

    private fun showTab(index: Int) {
        selectedTab = index
        title.text = listOf("Accueil", "Bibliothèque", "Forum", "Calendrier", "Mon profil")[index]
        content.removeAllViews()
        when (index) {
            0 -> home()
            1 -> documents()
            2 -> forum()
            3 -> calendar()
            else -> profile()
        }
    }

    private fun home() {
        content.addView(text("Bonjour${student.name.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}", 25, ink, true))
        content.addView(text("Ta plateforme académique ENSPY.", 14, muted).apply { setPadding(0, 4, 0, 18) })
        val quick = LinearLayout(this).horizontal()
        quick.addView(stat("Cours", "Bibliothèque", 1) { showTab(1) }, LinearLayout.LayoutParams(0, 100, 1f))
        quick.addView(stat("Forum", "Entraide", 2) { showTab(2) }, LinearLayout.LayoutParams(0, 100, 1f).apply { leftMargin = 10 })
        content.addView(quick)
        sectionTitle("Documents récents")
        val recent = LinearLayout(this).vertical()
        content.addView(recent)
        api.get("/api/documents/recent") { status, raw ->
            runOnUiThread {
                if (status in 200..299) {
                    val list = arrayFrom(raw).objects().take(4)
                    if (list.isEmpty()) recent.addView(empty("Aucun document récent."))
                    list.forEach { recent.addView(documentCard(it.asDocument())) }
                } else recent.addView(empty("Les documents seront disponibles dès que le serveur répondra."))
            }
        }
        sectionTitle("Prochains rendez-vous")
        val events = LinearLayout(this).vertical()
        content.addView(events)
        api.get("/api/events/upcoming") { status, raw ->
            runOnUiThread {
                if (status in 200..299) arrayFrom(raw).objects().take(3).forEach { events.addView(eventCard(it.asCalendarEvent())) }
                if (events.childCount == 0) events.addView(empty("Aucun événement à venir."))
            }
        }
    }

    private fun documents() {
        content.addView(text("Bibliothèque académique", 24, ink, true))
        content.addView(
            text(
                "Retrouve les cours, TD, TP, épreuves et corrigés.",
                14,
                muted
            ).apply { setPadding(0, 4, 0, 12) })

        // 1. DÉPLACER LA DÉCLARATION ICI (avant les listeners)
        val list = LinearLayout(this).vertical()

        val search = field("Rechercher un document ou une matière", false)
        content.addView(search)

        val types = LinearLayout(this).horizontal()
        val chosen = arrayOf("")

        listOf("Tous", "Cours", "TD", "TP", "Épreuves", "Corrigés").forEach { label ->
            val chip = TextView(this).apply {
                text = label
                setTextColor(orange)
                setPadding(14, 9, 14, 9)
                setBackgroundColor(Color.WHITE)
                setOnClickListener {
                    chosen[0] = if (label == "Tous") "" else label.lowercase().removeSuffix("s")
                        .replace("é", "e")
                    // Maintenant 'list' est reconnu ici !
                    loadDocuments(search.text.toString(), chosen[0], list)
                }
            }
            types.addView(chip, LinearLayout.LayoutParams(-2, 42).apply { rightMargin = 6 })
        }

        content.addView(HorizontalScrollView(this).apply { addView(types) })

        // 2. AJOUTER LA LISTE AU CONTENU (l'ordre d'affichage reste le même)
        content.addView(list)

        search.setOnEditorActionListener { _, _, _ ->
            loadDocuments(search.text.toString(), chosen[0], list)
            true
        }

        loadDocuments("", "", list)
    }

    private fun loadDocuments(search: String, type: String, list: LinearLayout) {
        list.removeAllViews()
        list.addView(text("Chargement…", 14, muted).apply { setPadding(0, 18, 0, 18) })
        api.documentsQuery(search, type) { status, raw ->
            runOnUiThread {
                list.removeAllViews()
                if (status in 200..299) {
                    val items = arrayFrom(raw).objects()
                    if (items.isEmpty()) list.addView(empty("Aucun document ne correspond à ta recherche."))
                    items.forEach { list.addView(documentCard(it.asDocument())) }
                } else list.addView(empty("Impossible de charger la bibliothèque."))
            }
        }
    }

    private fun forum() {
        content.addView(text("Forum ENSPY", 24, ink, true))
        content.addView(text("Pose une question, partage une méthode, aide un camarade.", 14, muted).apply { setPadding(0, 4, 0, 12) })
        val newPost = button("+ Nouvelle question", true)
        content.addView(newPost, LinearLayout.LayoutParams(-1, 52))
        newPost.setOnClickListener { createPostDialog() }
        val list = LinearLayout(this).vertical()
        content.addView(list)
        api.get("/api/forum/posts?sort=recent") { status, raw ->
            runOnUiThread {
                if (status in 200..299) {
                    val posts = arrayFrom(raw).objects()
                    posts.forEach { list.addView(postCard(it.asForumPost())) }
                    if (posts.isEmpty()) list.addView(empty("Le forum est prêt pour ta première question."))
                } else list.addView(empty("Impossible de charger le forum."))
            }
        }
    }

    private fun calendar() {
        content.addView(text("Calendrier académique", 24, ink, true))
        content.addView(text("Examens, cours, TP et événements spéciaux.", 14, muted).apply { setPadding(0, 4, 0, 14) })
        val list = LinearLayout(this).vertical()
        content.addView(list)
        api.get("/api/events") { status, raw ->
            runOnUiThread {
                if (status in 200..299) arrayFrom(raw).objects().forEach { list.addView(eventCard(it.asCalendarEvent())) }
                if (list.childCount == 0) list.addView(empty("Aucun événement enregistré."))
            }
        }
    }

    private fun profile() {
        content.addView(text("Mon profil", 24, ink, true))
        content.addView(text(student.name.ifBlank { "Étudiant ENSPY" }, 18, orange, true).apply { setPadding(0, 18, 0, 2) })
        content.addView(text(student.email, 14, muted))
        content.addView(text("${student.level.ifBlank { "Niveau 1" }} • ${student.filiere.ifBlank { "Filière à préciser" }}", 14, muted).apply { setPadding(0, 2, 0, 18) })
        listOf(
            "Calculatrice de moyennes" to { averageDialog() },
            "Mes favoris" to { favorites() },
            "Nouvelle contribution" to { contributionDialog() },
            "À propos & aide" to { aboutDialog() }
        ).forEach { (label, action) ->
            val row = button(label, false)
            content.addView(row, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 8 })
            row.setOnClickListener { action() }
        }
        val logout = button("Se déconnecter", false).apply { setTextColor(Color.RED) }
        content.addView(logout, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 10 })
        logout.setOnClickListener { store.clearSession(); showAuth() }
    }

    private fun documentCard(document: Document): View {
        val card = LinearLayout(this).horizontal().apply {
            setPadding(14)
            setBackgroundColor(Color.WHITE)
            isClickable = true
            setOnClickListener { documentDetail(document) }
        }
        val icon = text(document.docType.take(3).uppercase(), 13, orange, true).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.enspy_cream))
        }
        card.addView(icon, LinearLayout.LayoutParams(54, 54).apply { rightMargin = 12 })
        val body = LinearLayout(this).vertical()
        body.addView(text(document.title, 16, ink, true))
        body.addView(text("${document.subject.ifBlank { "Matière générale" }} • ${document.docType}", 13, muted).apply { setPadding(0, 4, 0, 0) })
        card.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        return card.apply { (layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, 0, 9) }
    }

    private fun documentDetail(document: Document) {
        val panel = LinearLayout(this).vertical().apply { setPadding(24) }
        panel.addView(text(document.title, 22, ink, true))
        panel.addView(text("${document.subject} • ${document.docType}", 14, orange).apply { setPadding(0, 8, 0, 12) })
        panel.addView(text(document.description.ifBlank { "Document pédagogique ENSPY." }, 15, muted))
        val read = button("Ouvrir le lecteur sécurisé", true)
        panel.addView(read, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 20 })
        val favorite = button("Ajouter aux favoris", false)
        panel.addView(favorite, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 8 })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        read.setOnClickListener {
            dialog.dismiss()
            val local = store.securePdfFile(document.id)
            if (local.exists()) {
                startActivity(Intent(this, PdfViewerActivity::class.java).putExtra("path", local.absolutePath).putExtra("title", document.title))
            } else if (document.fileUrl.isNotBlank()) {
                toast("Téléchargement sécurisé en cours…")
                store.downloadAndEncrypt(api.url(document.fileUrl), document.id) { ok, path ->
                    if (ok && path != null) startActivity(Intent(this, PdfViewerActivity::class.java).putExtra("path", path).putExtra("title", document.title))
                    else toast("Le document n'a pas pu être téléchargé.")
                }
            } else toast("Aucun fichier n'est associé à ce document.")
        }
        favorite.setOnClickListener {
            api.post("/api/favorites", JSONObject().put("documentId", document.id)) { status, _ ->
                if (status in 200..299) toast("Ajouté aux favoris.") else toast("Connexion requise pour les favoris.")
            }
        }
        dialog.show()
    }

    private fun postCard(post: ForumPost): View {
        val card = LinearLayout(this).vertical().apply {
            setPadding(15)
            setBackgroundColor(Color.WHITE)
        }
        card.addView(text(post.title, 17, ink, true))
        card.addView(text(post.content.take(150), 14, muted).apply { setPadding(0, 7, 0, 10) })
        val footer = LinearLayout(this).horizontal()
        footer.addView(text("${post.subject.ifBlank { "Général" }}  •  ${post.votes} votes  •  ${post.answers} réponses", 12, orange),
            LinearLayout.LayoutParams(0, -2, 1f))
        val up = TextView(this).apply {
            text = "▲"
            setTextColor(orange)
            setPadding(12, 0, 12, 0)
            setOnClickListener { votePost(post.id, 1) }
        }
        val down = TextView(this).apply {
            text = "▼"
            setTextColor(muted)
            setPadding(12, 0, 4, 0)
            setOnClickListener { votePost(post.id, -1) }
        }
        footer.addView(up)
        footer.addView(down)
        card.addView(footer)
        card.setOnClickListener { postDetail(post) }
        return card.apply { (layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 10, 0, 0) }
    }

    private fun votePost(postId: Int, value: Int) {
        api.post("/api/forum/posts/$postId/vote", JSONObject().put("value", value)) { status, _ ->
            if (status in 200..299) toast("Vote enregistré.") else toast("Connecte-toi pour voter.")
        }
    }

    private fun postDetail(post: ForumPost) {
        val panel = LinearLayout(this).vertical().apply { setPadding(22) }
        panel.addView(text(post.title, 21, ink, true))
        panel.addView(text(post.content, 15, muted).apply { setPadding(0, 12, 0, 12) })
        panel.addView(text("${post.votes} votes • ${post.answers} réponses", 13, orange))
        val answer = field("Écrire une réponse…", false)
        panel.addView(answer, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 18 })
        val send = button("Publier la réponse", true)
        panel.addView(send, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 8 })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        send.setOnClickListener {
            if (answer.text.isBlank()) return@setOnClickListener
            api.post("/api/forum/posts/${post.id}/answers", JSONObject().put("content", answer.text.toString())) { status, _ ->
                if (status in 200..299) { dialog.dismiss(); toast("Réponse publiée.") } else toast("Impossible de publier la réponse.")
            }
        }
        dialog.show()
    }

    private fun createPostDialog() {
        val panel = LinearLayout(this).vertical().apply { setPadding(22) }
        val titleInput = field("Titre de la question", false)
        val body = field("Décris ta question…", false)
        panel.addView(titleInput); panel.addView(body)
        val send = button("Publier", true)
        panel.addView(send, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 12 })
        val dialog = AlertDialog.Builder(this).setTitle("Nouvelle question").setView(panel).create()
        send.setOnClickListener {
            api.post("/api/forum/posts", JSONObject().put("title", titleInput.text.toString()).put("content", body.text.toString())) { status, _ ->
                if (status in 200..299) { dialog.dismiss(); toast("Question publiée."); showTab(2) } else toast("Connecte-toi pour publier.")
            }
        }
        dialog.show()
    }

    private fun contributionDialog() {
        val panel = LinearLayout(this).vertical().apply { setPadding(22) }
        val titleInput = field("Titre du document", false)
        val url = field("Lien sécurisé du fichier PDF", false)
        val description = field("Description", false)
        panel.addView(titleInput); panel.addView(url); panel.addView(description)
        val submit = button("Envoyer la contribution", true)
        panel.addView(submit, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 12 })
        val dialog = AlertDialog.Builder(this).setTitle("Partager une ressource").setView(panel).create()
        submit.setOnClickListener {
            api.post("/api/contributions", JSONObject().put("title", titleInput.text.toString())
                .put("fileUrl", url.text.toString()).put("description", description.text.toString())
                .put("docType", "cours")) { status, _ ->
                if (status in 200..299) { dialog.dismiss(); toast("Merci, ta contribution est en attente de validation.") }
                else toast("Vérifie les informations saisies.")
            }
        }
        dialog.show()
    }

    private fun averageDialog() {
        val panel = LinearLayout(this).vertical().apply { setPadding(22) }
        val values = field("Notes séparées par des virgules (ex: 12, 14, 9)", false)
        val coefficients = field("Coefficients séparés par des virgules (ex: 2, 1, 3)", false)
        panel.addView(values); panel.addView(coefficients)
        val result = text("Résultat : —", 18, orange, true).apply { setPadding(0, 16, 0, 0) }
        panel.addView(result)
        val calculate = button("Calculer ma moyenne", true)
        panel.addView(calculate, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 14 })
        calculate.setOnClickListener {
            val notes = values.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            val coefs = coefficients.text.toString().split(",").mapNotNull { it.trim().toDoubleOrNull() }
            if (notes.isEmpty() || notes.size != coefs.size || coefs.sum() == 0.0) result.text = "Vérifie le nombre de notes et de coefficients."
            else result.text = "Résultat : %.2f / 20".format(notes.zip(coefs).sumOf { it.first * it.second } / coefs.sum())
        }
        AlertDialog.Builder(this).setTitle("Calculatrice de moyenne").setView(panel).setPositiveButton("Fermer", null).show()
    }

    private fun favorites() {
        api.get("/api/favorites") { status, raw ->
            runOnUiThread {
                val panel = LinearLayout(this).vertical().apply { setPadding(22) }
                if (status in 200..299) arrayFrom(raw).objects().forEach { panel.addView(documentCard(it.asDocument())) }
                if (panel.childCount == 0) panel.addView(empty("Aucun favori pour le moment."))
                AlertDialog.Builder(this).setTitle("Mes favoris").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("Fermer", null).show()
            }
        }
    }

    private fun showNotifications() {
        api.get("/api/notifications") { status, raw ->
            runOnUiThread {
                val panel = LinearLayout(this).vertical().apply { setPadding(22) }
                if (status in 200..299) arrayFrom(raw).objects().forEach {
                    panel.addView(text(it.optString("title", "Notification"), 16, ink, true))
                    panel.addView(text(it.optString("message", it.optString("body")), 14, muted).apply { setPadding(0, 4, 0, 12) })
                }
                if (panel.childCount == 0) panel.addView(empty("Tu n'as pas de nouvelle notification."))
                AlertDialog.Builder(this).setTitle("Notifications").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("Fermer", null).show()
                if (status in 200..299) api.patch("/api/notifications/read-all", JSONObject()) { _, _ -> }
            }
        }
    }

    private fun aboutDialog() {
        val panel = LinearLayout(this).vertical().apply { setPadding(dp(22)) }
        panel.addView(text("ENSPY Étudiants", 23, orange, true))
        panel.addView(text("La plateforme académique numérique de l'École Nationale Supérieure Polytechnique de Yaoundé.", 15, muted).apply { setPadding(0, dp(8), 0, dp(18)) })
        panel.addView(text("Développé par DevOps Abess", 14, ink, true))
        panel.addView(text("+237 682 229 367", 14, muted).apply { setPadding(0, dp(4), 0, 0) })
        val whatsapp = button("Contacter le développeur sur WhatsApp", true)
        panel.addView(whatsapp, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        whatsapp.setOnClickListener { contactDeveloper() }
        AlertDialog.Builder(this).setView(panel).setPositiveButton("Fermer", null).show()
    }

    private fun eventCard(event: CalendarEvent): View {
        val card = LinearLayout(this).horizontal().apply { setPadding(14); setBackgroundColor(Color.WHITE) }
        card.addView(text(event.eventType.uppercase(), 11, orange, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(68, 58))
        val body = LinearLayout(this).vertical().apply { setPadding(12, 0, 0, 0) }
        body.addView(text(event.title, 16, ink, true))
        body.addView(text("${event.startsAt.ifBlank { "Date à confirmer" }}${event.location.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}", 13, muted).apply { setPadding(0, 5, 0, 0) })
        card.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        return card.apply { (layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, 0, 9) }
    }

    private fun stat(number: String, label: String, tab: Int, action: () -> Unit): View =
        LinearLayout(this).vertical().apply {
            setPadding(14)
            setBackgroundColor(Color.WHITE)
            addView(text(number, 21, orange, true))
            addView(text(label, 13, muted).apply { setPadding(0, 5, 0, 0) })

            // CORRECTION ICI : Ajoute { _ -> ... }
            setOnClickListener { _ -> action() }
        }

    private fun sectionTitle(value: String) {
        content.addView(text(value, 18, ink, true).apply { setPadding(0, 22, 0, 10) })
    }

    private fun field(hint: String, secret: Boolean): EditText =
        EditText(this).apply {
            this.hint = hint
            setTextSize(15f)
            setTextColor(ink)
            setHintTextColor(muted)
            setSingleLine(true)
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(0, 5, 0, 5)
            layoutParams = LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 7 }
        }

    private fun authField(hint: String, secret: Boolean): EditText =
        EditText(this).apply {
            this.hint = hint
            setTextSize(16f)
            setTextColor(ink)
            setHintTextColor(muted)
            includeFontPadding = true
            setSingleLine(true)
            if (secret) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            background = rounded(Color.WHITE, dp(13), R.color.enspy_line)
            setPadding(dp(14), dp(5), dp(14), dp(5))
            layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(8)
            }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(fillColor: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            strokeColor?.let { setStroke(dp(1), ContextCompat.getColor(this@MainActivity, it)) }
        }

    private fun verticalGradient(top: Int, bottom: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = 0f
        }

    private fun whatsappButton(): Button =
        Button(this).apply {
            text = "DevOps-Abess♨️"
            isAllCaps = false
            setTextSize(13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.enspy_whatsapp))
            background = rounded(Color.WHITE, dp(14), R.color.enspy_whatsapp)
            minHeight = 0
            minWidth = 0
            stateListAnimator = null
        }

    private fun contactDeveloper() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/237682229367?text=Bonjour%20DevOps%20Abess")))
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            setTextSize(size.toFloat())
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun button(label: String, filled: Boolean): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextSize(14f)
            setTypeface(null, Typeface.BOLD)
            minHeight = 0
            minWidth = 0
            stateListAnimator = null
            setPadding(dp(12), 0, dp(12), 0)
            if (filled) {
                setTextColor(Color.WHITE)
                background = rounded(orange, dp(14))
            } else {
                setTextColor(orange)
                background = rounded(Color.WHITE, dp(14), R.color.enspy_orange)
            }
        }

    private fun empty(message: String): View = text(message, 14, muted).apply { setPadding(0, 18, 0, 18) }

    private fun parseResponse(raw: String): JSONObject {
        val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return obj.optJSONObject("data") ?: obj
    }

    private fun arrayFrom(raw: String): JSONArray {
        val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        return obj.dataArray() ?: runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}