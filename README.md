# ENSPY Android — Livrable 2

Application Android native Kotlin pour les étudiants de l'École Nationale Supérieure Polytechnique de Yaoundé.

## Ouvrir et compiler

1. Ouvrir le dossier `ENSPY_Android_Final` dans Android Studio.
2. Laisser Gradle synchroniser le projet.
3. Vérifier qu'un émulateur Android ou un téléphone est connecté.
4. Lancer la configuration `app`.

Le projet conserve le Gradle Wrapper fourni dans le ZIP source.

## URL de l'API

L'URL par défaut est `http://10.0.2.2:8080`, adaptée à l'émulateur Android lorsqu'une API tourne sur le poste hôte.

Pour une API déployée, modifier la ligne `buildConfigField("String", "API_BASE_URL", "...")` dans `app/build.gradle.kts`, puis synchroniser Gradle.

Le mode HTTP est activé pour le développement avec l'émulateur. En production, utiliser une URL `https://` et désactiver `android:usesCleartextTraffic` dans `AndroidManifest.xml`.

## Fonctionnalités livrées

- Connexion, inscription et déconnexion via l'API ENSPY.
- Session chiffrée localement avec Android Keystore.
- Anti-capture d'écran et anti-enregistrement via `FLAG_SECURE` sur tous les écrans.
- Bibliothèque de documents avec recherche et filtres par type.
- Téléchargement des PDF dans le stockage privé de l'application, chiffré AES-GCM.
- Lecteur PDF interne avec navigation, recherche de page et zoom tactile.
- Forum : liste, publication de question, vote via l'API et réponses.
- Calendrier des examens et événements.
- Favoris, notifications et marquage comme lu.
- Calculatrice de moyenne pondérée avec coefficients.
- Soumission de contribution documentaire.
- Page profil, informations développeur et contact WhatsApp.

## Compte de test du Livrable 1

- E-mail : `admin@enspy.cm`
- Mot de passe : `admin123`

## Sécurité

Les fichiers chiffrés ne sont pas écrits dans les téléchargements publics, la galerie ou le stockage externe. Les préférences contenant le token et les fichiers sécurisés sont exclus des sauvegardes Android.

Le projet ne contient aucun secret ni identifiant d'accès codé en dur.