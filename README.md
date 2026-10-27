# TPE Bridge Android — The Barber One
Application Android native (Android 7.0+, API 24) séparée du dépôt salon-queue.

## Fonctionnement
- Paiements : polling HTTPS vers le serveur, puis protocole Nepting Concert v3 IP sur le réseau local.
- Impression automatique : polling HTTPS des tickets, puis envoi ESC/POS brut vers une imprimante réseau (Ethernet ou Wi-Fi), TCP 9100 par défaut.
- Configuration indépendante : IP et port du TPE, IP et port de l'imprimante.
- Modes pris en charge : escpos (tickets et coupe), drawer (ouverture de tiroir) et text.
- Pour la sortie CP850, le programme sélectionne ESC t 2 (la valeur peut varier selon le firmware de l'imprimante).
- L'accusé de réception signifie que les octets ont été transmis au socket, **pas** que le papier est physiquement sorti.
- Une imprimante utilisant uniquement IPP, AirPrint ou un protocole propriétaire ne fonctionnera pas avec cette version.

## Compilation
GitHub Actions > Build Android APK > Artifacts > tpe-bridge-android-7plus-apk.

## Sécurité et essais
**Prototype de test, pas encore validé pour les paiements réels.**
Ne pas utiliser simultanément le bridge Windows et Android pour les mêmes paiements ou impressions.
Ne pas relancer un paiement incertain avant vérification du TPE.
La clé du bridge est stockée dans les préférences de l'application : prévoir un stockage chiffré avant la production.
Vérifier sur un appareil Android 7 réel et une imprimante ESC/POS réseau : accents, euro, coupe, tiroir, connexion perdue, duplications et veille.
