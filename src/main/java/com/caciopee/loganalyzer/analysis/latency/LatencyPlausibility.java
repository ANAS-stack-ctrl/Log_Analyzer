package com.caciopee.loganalyzer.analysis.latency;

/**
 * Garde de plausibilité des durées « took [N] ms » des logs WORKS.
 *
 * <p>WORKS calcule {@code took = epochFin - epochDébut}, où {@code epochDébut} est lu dans un
 * champ du log. Pour certains flux (écrans d'impression {@code FxSCRN}, certains SAVE…), ce
 * champ de début est périmé/corrompu : le {@code took} affiche alors des durées impossibles
 * (25 h, 61 h…) alors que l'opération réelle a duré quelques instants.</p>
 *
 * <p>Calibré sur tout le dataset (39 212 événements {@code took ≥ 10 s}) :
 * <ul>
 *   <li>opérations <b>réelles</b> : même jour, jusqu'à ~3,3 h (gros export 656 976 lignes) ;</li>
 *   <li><b>artefacts</b> : multi-jours, ≥ 13 h (jusqu'à 61 h), tous dus à un horodatage de
 *       début périmé ({@code took = fin − début} avec un début situé plusieurs jours avant) ;</li>
 *   <li>aucune valeur entre 3,3 h et 13 h.</li>
 * </ul>
 * Le plafond est posé dans cette zone vide (6 h) : il conserve 100 % des lenteurs réelles
 * observées et signale 100 % des artefacts, avec une marge ×4.</p>
 */
public final class LatencyPlausibility {

    /** Au-delà de cette durée, un « took » d'opération WORKS est jugé non fiable (zone vide 3,3 h–13 h). */
    public static final long IMPLAUSIBLE_MS = 21_600_000L; // 6 heures

    private LatencyPlausibility() {
    }

    public static boolean isImplausible(Long durationMs) {
        return durationMs != null && durationMs > IMPLAUSIBLE_MS;
    }

    /** Message client expliquant pourquoi la durée brute est ignorée. */
    public static String reason(Long durationMs) {
        return "Le journal indique " + formatApprox(durationMs)
                + ", ce qui est incompatible avec une opération interactive. "
                + "Il s'agit d'une anomalie de mesure WORKS (horodatage de début erroné) : "
                + "la durée réelle est bien plus courte. Cette valeur est ignorée dans l'analyse.";
    }

    private static String formatApprox(Long ms) {
        if (ms == null || ms <= 0) {
            return "une durée invalide";
        }
        double hours = ms / 3_600_000.0;
        if (hours >= 1) {
            return String.format(java.util.Locale.FRANCE, "~%.1f h", hours);
        }
        return String.format(java.util.Locale.FRANCE, "~%.0f min", ms / 60_000.0);
    }
}
