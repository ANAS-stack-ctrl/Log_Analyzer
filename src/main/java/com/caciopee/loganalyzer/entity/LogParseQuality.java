package com.caciopee.loganalyzer.entity;
public enum LogParseQuality {


    HEALTHY,
    /**
     * ✅ Ligne parfaitement parsée
     *
     * - Tous les champs importants sont présents
     * - Structure respectée
     * - Pas d’ambiguïté dans le message
     *
     * 👉 Cas idéal (logs propres)
     */

    WITH_EMPTY_FIELDS,
    /**
     * ⚠️ Ligne parsée mais avec des champs vides
     *
     * - Certains champs sont null (ex: processName, environment…)
     * - MAIS la structure globale reste correcte
     *
     * 👉 Très fréquent dans perfs_works, memperfs_works
     */

    AMBIGUOUS_MESSAGE,
    /**
     * ⚠️ Message ambigu
     *
     * - Le message contient des séparateurs '|' internes
     * - Le découpage peut être incertain
     *
     * 👉 Exemple :
     * filter code [xxx|yyy|zzz]
     *
     * 👉 Risque de décalage ou mauvaise interprétation
     */

    INCOMPLETE,

    /**
     * ❌ Ligne cassée / non exploitable
     *
     * - Structure totalement invalide
     * - Pas assez de colonnes
     * - Impossible de parser
     *
     * 👉 Exemple :
     * lignes corrompues ou format inconnu
     */
    BROKEN
    /**
     * ⚠️ Ligne incomplète ou partiellement exploitable
     *
     * - Certains champs critiques manquent (timestamp, level…)
     * - Ou fin de ligne non fiable
     *
     * 👉 Exemple :
     * logs tronqués ou mal formés
     */
}