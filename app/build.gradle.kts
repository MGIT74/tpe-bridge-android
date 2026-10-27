plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.thebarberone.tpebridge"
 compileSdk = 35
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 defaultConfig { applicationId = "com.thebarberone.tpebridge"; minSdk = 24; targetSdk = 28; versionCode = 1; versionName = "1.0" }
}
