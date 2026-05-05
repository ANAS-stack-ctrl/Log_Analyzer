package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.entity.LogEventType;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogBusinessExplanationService {

    public String explain(LogEventType eventType,
                          String message,
                          String processName,
                          String sourceClass,
                          String level,
                          String defaultMeaning) {

        String msg = safe(message);
        String lower = msg.toLowerCase(Locale.ROOT);

        if (msg.isBlank()) {
            return "Message vide ou non exploitable.";
        }

        // ===== SCHEDULED TRIGGER =====
        if (lower.contains("trigger_every") || lower.contains("triggerrules_every")) {
            return buildExplanation(
                    "Exécution automatique planifiée",
                    "Le système déclenche un traitement automatique à intervalle régulier.\n\n"
                            + "Détails :\n"
                            + "- Exécution périodique configurée dans le scheduler\n"
                            + "- Traitement en arrière-plan\n"
                            + "- Aucun utilisateur directement impliqué\n\n"
                            + "Interprétation métier :\n"
                            + "Ce traitement permet de maintenir le système à jour, par exemple par vérification, synchronisation ou exécution périodique de règles métier.",
                    extractExecutionTime(msg)
            );
        }

        // ===== PROCESS FILTER REG =====
        if (lower.contains("processfilter") && lower.contains("find_tracabilite_ws_reg")) {
            String uuid = extractUuid(msg);

            return "Le système lance un processus de filtrage pour la tracabilité REG"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape permet d'appliquer des filtres métier pour récupérer les informations de tracabilité.";
        }

        // ===== WS_FIND_TRACABILITE_ALGESIRAS / TRACEABILITY SEARCH DETAILS =====
        if (lower.contains("filter code [rule_ws_findtracabilitealgesiras_0.java]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String noUnite = extract(msg, "comp0\\.string1\\s*=\\s*([^\\s\\|]+)");
            String noService = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");
            String dateMin = extract(msg, "TO_TIMESTAMP\\(\\s*([^,]+)");

            if (noUnite != null) {
                return "Le système exécute la requête principale de recherche de traçabilité Algesiras"
                        + valuePart(". Unité recherchée", noUnite)
                        + valuePart(". Date minimale", dateMin)
                        + valuePart(". uuid", uuid)
                        + ". La requête cherche les traces actives non refusées, liées aux services AMPE/AMPI.";
            }

            return "Le système recherche un service portuaire pour reconstruire la traçabilité Algesiras"
                    + valuePart(". noService recherché", noService)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape sert à retrouver le service de base avant de charger les détails de traçabilité.";
        }

        if (lower.contains("wvstatuspdadiff")) {
            String value = extractBracketAfter(msg, "wvstatusPDAdiff");

            return "Le système applique un critère d’exclusion sur le statut PDA"
                    + valuePart(". Statut exclu", value)
                    + ". Les traces avec ce statut, par exemple Refuser, ne seront pas prises en compte.";
        }

        if (lower.contains("wstatuspda")) {
            String field = extractBracketAfter(msg, "wstatusPDA");

            return "Le système indique que le champ métier utilisé pour le statut PDA est"
                    + valuePart("", field)
                    + ".";
        }

        if (lower.contains("wvdatecreationsupegal")) {
            String date = extractBracketAfter(msg, "wvdateCreationsupEgal");

            return "Le système applique une date minimale de recherche"
                    + valuePart(". Date minimale", date)
                    + ". Les traces plus anciennes ne sont pas prises en compte.";
        }

        if (lower.contains("wvuniteegal") || lower.contains("wvnouniteegal")) {
            String unite = extractBracketAfter(msg, "wvnoUniteegal");
            if (unite == null) {
                unite = extractBracketAfter(msg, "wvUniteegal");
            }

            return "Le système filtre la recherche sur une unité précise"
                    + valuePart(". Unité", unite)
                    + ". Cette valeur permet de reconstruire le parcours de cette unité uniquement.";
        }

        if (lower.contains("wnounite")) {
            String field = extractBracketAfter(msg, "wnoUnite");

            return "Le système indique que le champ métier utilisé pour identifier l’unité est"
                    + valuePart("", field)
                    + ".";
        }

        if (lower.contains("fobjetfret")) {
            String rel = extractBracketAfter(msg, "fobjetfret");

            return "Le système utilise la relation objetFret pour rattacher le service portuaire à l’unité de fret"
                    + valuePart(". Relation", rel)
                    + ". Cette relation permet de passer du service vers l’objet physique suivi.";
        }

        if (lower.contains("wvsenotin")) {
            String excluded = extractBracketAfter(msg, "wvsenotin");

            return "Le système exclut certains états de service de la recherche"
                    + valuePart(". États exclus", excluded)
                    + ". Cela évite de prendre en compte des services clôturés ou non pertinents.";
        }

        if (lower.contains("wvhaschangedegal")) {
            String value = extractBracketAfter(msg, "wvhasChangedegal");

            return "Le système filtre les services selon l’indicateur hasChanged"
                    + valuePart(". Valeur attendue", value)
                    + ". Ici, false signifie que le système cherche des services non modifiés.";
        }

        // ===== AUTOMATIC RULES / BEFORE_LOAD / LOAD =====
        if (lower.contains("call runrules ws from")) {
            String ip = extract(msg, "from\\s+([0-9\\.]+)");

            return "Un service distant demande l’exécution des règles métier"
                    + valuePart(". Adresse IP source", ip)
                    + ". Cette ligne indique qu’un traitement web service déclenche le moteur de règles.";
        }

        if (lower.contains("automaticprocessing") && lower.contains("automatic launch") && lower.contains("firerules")) {
            String service = extract(msg, "\\[\\[([^\\]]+)]]");

            return "Le système lance automatiquement les règles métier"
                    + valuePart(". Service concerné", service)
                    + ". Ce déclenchement est automatique et prépare l’exécution du workflow.";
        }

        if (lower.contains("taskName [WS_FIND_TRACABILITE_ALGESIRAS]".toLowerCase(Locale.ROOT))
                && lower.contains("actionName [BEFORE_LOAD]".toLowerCase(Locale.ROOT))) {
            String uuid = extractUuid(msg);

            return "Le système prépare la phase BEFORE_LOAD de la recherche de traçabilité Algesiras"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape initialise le traitement avant le chargement réel des données.";
        }

        if (lower.contains("taskName [WS_FIND_TRACABILITE_ALGESIRAS]".toLowerCase(Locale.ROOT))
                && lower.contains("actionName [LOAD]".toLowerCase(Locale.ROOT))) {
            String uuid = extractUuid(msg);

            return "Le système exécute la phase LOAD de la recherche de traçabilité Algesiras"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape charge les services, traces et détails nécessaires pour reconstruire le parcours.";
        }


        // ===== SPECIFIC BUSINESS SEARCHES / BEFORE DYNAMIC PARAMETERS =====

        // ===== BAD_REG (ANOMALIE / BAD EQUIPMENT) =====
        if (lower.contains("filter code [bad_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String refBad = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche une anomalie (BAD)"
                    + valuePart(". Référence BAD", refBad)
                    + valuePart(". uuid", uuid)
                    + ". Cette opération permet de vérifier si un équipement ou un élément est signalé comme défectueux ou problématique.";
        }

        // ===== CHECKSCAN =====
        if (lower.contains("filter code [checkscan]")) {
            String uuid = extractUuid(msg);

            return "Le système effectue une vérification de scan (CHECKSCAN)"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape permet de valider si un scan ou une opération de lecture est conforme.";
        }

        // ===== WS_SEARCH_AMP / WS_SEARCH_AMP_F =====
        if (lower.contains("filter code [ws_search_amp_f]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String noService = extract(msg, "det_comp_fils0\\.string_value\\s*=\\s*([^\\s\\|]+)");
            String codeBPartner = extract(msg, "comp1\\.string8\\s*=\\s*([^\\s\\|]+)");

            return "Le système exécute une recherche AMP via WS_SEARCH_AMP_F"
                    + valuePart(". noService recherché", noService)
                    + valuePart(". Code partenaire", codeBPartner)
                    + valuePart(". uuid", uuid)
                    + ". Cette requête recherche un service portuaire actif AMPI/AMPE, non supprimé, non clôturé, non annulé et lié au partenaire demandé.";
        }

        if (lower.contains("filter code [ws_search_amp_f]") && lower.contains("classname [serviceportuaire]")) {
            String uuid = extractUuid(msg);

            return "Le système prépare une recherche de ServicePortuaire pour WS_SEARCH_AMP_F"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape sert à retrouver l’AMP correspondant au numéro de service et au partenaire.";
        }

        // ===== WS_SEARCH_REG / LASTPOINT_REG / SCAN_REG / EBOOK_REG / FIND_TRACABILITE_WS_REG =====
        if (lower.contains("filter code [ws_search_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);
            String date = extract(msg, "TO_TIMESTAMP\\(\\s*([^,]+)");
            String noUnite = extract(msg, "string1\\s*=\\s*([^\\s\\|]+)");
            if (noUnite == null) {
                noUnite = extract(msg, "det_comp_fils1\\.string_value\\s*=\\s*([^\\s\\|]+)");
            }
            String amp = extract(msg, "string2\\s*=\\s*([^\\s\\|]+)");

            return "Le système exécute une recherche avancée de données de traçabilité"
                    + valuePart(". Date minimale", date)
                    + valuePart(". Unité", noUnite)
                    + valuePart(". AMP", amp)
                    + valuePart(". uuid", uuid)
                    + ". La requête SQL effectue des jointures entre plusieurs tables WORKS (composants, relations et détails), "
                    + "puis applique des filtres métier comme noUnite, nomService AMPE/AMPI, hasChanged et SE. "
                    + "Le système cherche des objets de traçabilité correspondant à des critères précis afin de récupérer les informations métier utiles."
                    + durationPart(duration);
        }

        if (lower.contains("filter code [lastpoint_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String date = extract(msg, "date2\\s*>=\\s*TO_TIMESTAMP\\(\\s*([^,]+)");
            if (date == null) {
                date = extract(msg, "TO_TIMESTAMP\\(\\s*([^,]+)");
            }
            String amp = extract(msg, "string2\\s*=\\s*([^\\s\\|]+)");
            String unite = extract(msg, "string1\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche le dernier point de tracabilité (LASTPOINT_REG)"
                    + valuePart(". Date minimale", date)
                    + valuePart(". AMP", amp)
                    + valuePart(". Unité", unite)
                    + valuePart(". uuid", uuid)
                    + ". Cette requête permet d’identifier la dernière position ou le dernier état connu d’un flux.";
        }

        if (lower.contains("filter code [scan_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String date = extract(msg, "dateMessage\\s*>=\\s*TO_TIMESTAMP\\(\\s*([^,]+)");
            if (date == null) {
                date = extract(msg, "TO_TIMESTAMP\\(\\s*([^,]+)");
            }
            String amp = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return buildExplanation(
                    "Scan basé sur des critères métier et temporels",
                    "Le système exécute une recherche de type scan.\n\n"
                            + "Détails :\n"
                            + "- Filtrage basé sur un identifiant AMP" + valuePart(" :", amp) + "\n"
                            + "- Filtrage basé sur une date" + valuePart(" :", date) + "\n"
                            + "- Requête SQL combinant plusieurs conditions\n\n"
                            + "Interprétation métier :\n"
                            + "Le système cherche des éléments spécifiques en fonction d’un identifiant et d’une période donnée."
                            + valuePart("\n\nuuid", uuid),
                    extractExecutionTime(msg)
            );
        }


        if (lower.contains("filter code [ebook_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String amp = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche un Ebook associé à un AMP"
                    + valuePart(". AMP", amp)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape sert à récupérer les données numériques liées au service.";
        }

        if (lower.contains("filter code [find_tracabilite_ws_reg]") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String noService = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système exécute une recherche de tracabilité via Find_Tracabilite_WS_REG"
                    + valuePart(". noService", noService)
                    + valuePart(". uuid", uuid)
                    + ". Cette requête vise à retrouver les données de tracabilité associées à un service spécifique.";
        }

        if (lower.contains("ws_find_tracabilite_algesiras") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String noService = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche la tracabilité spécifique pour Algesiras"
                    + valuePart(". noService", noService)
                    + valuePart(". uuid", uuid)
                    + ". Cette requête vise à récupérer les informations liées à un service portuaire spécifique.";
        }

        if (lower.contains("ws_find_tracabilite_algesiras")
                && !lower.contains("start find service")
                && !lower.contains("end find service")
                && !lower.contains("listservices")
                && !lower.contains("trcheckpoint")
                && !lower.contains("sas import")
                && !lower.contains("query:")) {
            return buildExplanation(
                    "Recherche de traçabilité spécifique (Algesiras)",
                    "Le système exécute un web service spécifique pour rechercher des données de traçabilité liées à Algesiras.\n\n"
                            + "Détails détectés :\n"
                            + "- Utilisation d’une règle métier dédiée (Rule_WS_FindTracabiliteAlgesiras)\n"
                            + "- Recherche basée sur un identifiant métier (noService)\n"
                            + "- Exécution d’une requête SQL pour récupérer les composants\n"
                            + "- Chargement des données associées (attributs, relations)\n"
                            + "- Utilisation du multi-threading pour optimiser les performances\n\n"
                            + "Interprétation métier :\n"
                            + "Le système tente de retrouver des informations de traçabilité portuaire en utilisant un identifiant précis, avec un traitement optimisé.",
                    extractExecutionTime(msg)
            );
        }

        // ===== LAST POINT REG =====
        if (lower.contains("lastpoint_reg")
                && !lower.contains("aucune")
                && !lower.contains("fire rules")
                && !lower.contains("running rules")
                && !lower.contains("saveprocesscontent")
                && !lower.contains("row fetched")
                && !lower.contains("query:")
                && !lower.contains("preparesearchbyroot")) {
            return buildExplanation(
                    "Récupération du dernier point de traçabilité",
                    "Le système recherche le dernier état enregistré pour un élément REG.\n\n"
                            + "Détails :\n"
                            + "- Dernière position connue\n"
                            + "- Dernier statut\n"
                            + "- Dernière activité\n\n"
                            + "Interprétation métier :\n"
                            + "Permet de savoir où en est l’objet dans son cycle de vie.\n\n"
                            + "Exemple :\n"
                            + "Dernière étape d’un service ou d’un flux logistique.",
                    extractExecutionTime(msg)
            );
        }

        if (lower.contains("rule_ctrl_insertbad") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String refBad = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système vérifie l’existence d’un BAD actif avant insertion"
                    + valuePart(". Référence BAD", refBad)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape empêche les doublons et garantit que l’équipement est actif.";
        }

        // ===== SPECIFIC QUERY PARAMETERS =====
        if (lower.contains("wvdateactionsupegal")) {
            String date = extractBracketAfter(msg, "wvdateActionsupEgald");
            if (date == null) {
                date = extractBracketAfter(msg, "wvdateActionsupEgal");
            }

            return "Le système applique un filtre sur la date d’action"
                    + valuePart(". Date minimale", date)
                    + ". Seules les actions après cette date sont prises en compte.";
        }

        if (lower.contains("wvdatecreationsupegal")) {
            String date = extractBracketAfter(msg, "wvdateCreationsupEgald");
            if (date == null) {
                date = extractBracketAfter(msg, "wvdateCreationsupEgal");
            }

            return "Le système filtre les enregistrements à partir d’une date minimale"
                    + valuePart(". Date", date)
                    + ". Cela permet de limiter la recherche aux événements récents.";
        }

        if (lower.contains("wvnoserviceegal")) {
            String value = extractBracketAfter(msg, "wvnoServiceegal");

            return "Le système filtre par numéro de service"
                    + valuePart(". noService", value)
                    + ".";
        }

        if (lower.contains("wvnouniteegal") || lower.contains("wvuniteegal")) {
            String value = extractBracketAfter(msg, "wvnoUniteegal");
            if (value == null) {
                value = extractBracketAfter(msg, "wvUniteegal");
            }

            return "Le système filtre sur une unité spécifique"
                    + valuePart(". Unité", value)
                    + ".";
        }

        if (lower.contains("wvampegal")) {
            String amp = extractBracketAfter(msg, "wvampegal");

            return "Le système filtre la recherche sur un AMP spécifique"
                    + valuePart(". AMP", amp)
                    + ". Cela permet de cibler un environnement ou un service précis.";
        }

        if (lower.contains("wvallstatenotlike")) {
            String value = extractBracketAfter(msg, "wvallStatenotlike");

            return "Le système exclut les services dont l’état global correspond à un motif annulé"
                    + valuePart(". Motif exclu", value)
                    + ". Cela évite de retourner des AMP annulés.";
        }

        if (lower.contains("wallstate")) {
            String field = extractBracketAfter(msg, "wallState");

            return "Le système utilise le champ allState pour contrôler l’état global du service"
                    + valuePart(". Champ", field)
                    + ".";
        }

        if (lower.contains("fbpartner")) {
            String relation = extractBracketAfter(msg, "fbpartner");

            return "Le système utilise la relation bpartner"
                    + valuePart(". Relation", relation)
                    + ". Cette relation permet de rattacher le service portuaire au partenaire commercial concerné.";
        }

        if (lower.contains("wvwcodeegal")) {
            String code = extractBracketAfter(msg, "wvwcodeegal");

            return "Le système filtre la recherche par code partenaire"
                    + valuePart(". Code partenaire", code)
                    + ". Cette valeur permet de retrouver l’AMP liée au bon client ou partenaire.";
        }

        if (lower.contains("fobjetfret")) {
            String field = extractBracketAfter(msg, "fobjetfret");

            return "Le système utilise le champ objet fret"
                    + valuePart(". Champ", field)
                    + ". Ce champ représente le type de marchandise ou de transport.";
        }

        if (lower.contains("fv214") || lower.contains("fv515")) {
            String type = extractBracketAfter(msg, "fv");
            if (type == null) {
                type = extract(msg, "\\bfv(214|515)\\s*=\\[([^\\]]+)]");
            }

            return "Le système précise le type de composant recherché"
                    + valuePart(". Type", type)
                    + ". Cela correspond à une classe métier (Tracabilite, Scan, etc.).";
        }

        // ===== BATCH INSERT RELATIONS =====
        if (lower.contains("insertarrayrel") && lower.contains("child")) {
            return buildExplanation(
                    "Insertion de relations entre objets",
                    "Le système insère plusieurs relations entre composants.\n\n"
                            + "Détails :\n"
                            + "- Insertion en masse (batch)\n"
                            + "- Table relationnelle parent / enfant\n"
                            + "- Volume détecté : plusieurs éléments\n\n"
                            + "Interprétation métier :\n"
                            + "Le système construit les liens entre objets métiers.\n\n"
                            + "Attention :\n"
                            + "Cette opération peut devenir coûteuse en performance si le volume augmente.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CONTROL DUM =====
        if (lower.contains("ctrl_dum")) {
            return buildExplanation(
                    "Contrôle des données du dossier DUM",
                    "Le système effectue une vérification des données liées au dossier douanier.\n\n"
                            + "Détails :\n"
                            + "- Validation du moyen de transport\n"
                            + "- Vérification des informations associées\n\n"
                            + "Interprétation métier :\n"
                            + "Le système s’assure que les données du dossier sont cohérentes avant traitement.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SAVE DUM DOUANE =====
        if (lower.contains("ws_save_dum_douane")) {
            return buildExplanation(
                    "Traitement de sauvegarde d’un dossier douanier",
                    "Le système enregistre ou met à jour un dossier DUM côté douane.\n\n"
                            + "Étapes :\n"
                            + "- Suppression des anciennes relations\n"
                            + "- Insertion des nouvelles relations\n"
                            + "- Sauvegarde des données principales\n\n"
                            + "Interprétation métier :\n"
                            + "Le système met à jour les informations liées à un dossier douanier.\n\n"
                            + "Exemple :\n"
                            + "Le numéro DUM peut être utilisé pour identifier le dossier traité.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_SAVE_VISITE / VISITE AMP =====
        if (lower.contains("ws_save_visite")) {
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Sauvegarde d’une visite (Visite AMP / Service Portuaire). "
                    + "Le système exécute une opération métier de sauvegarde d’une visite"
                    + valuePart(". uuid", uuid)
                    + ". Étapes possibles détectées : déclenchement du web service WS_SAVE_VISITE, exécution des règles métier, "
                    + "nettoyage des objets à supprimer, sauvegarde des données principales VisiteAMP ou ServicePortuaire, "
                    + "sauvegarde des relations entre objets et mesure du temps total. "
                    + "Interprétation métier : une visite ou un objet associé est en cours d’enregistrement dans WORKS."
                    + durationPart(duration);
        }

        // ===== DUM / BAD / RFID SPECIFIC BUSINESS EVENTS =====
        if (lower.contains("ws_save_bad")) {
            String uuid = extractUuid(msg);

            return "Le système enregistre une anomalie (BAD)"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape correspond à la création ou mise à jour d’un équipement défectueux dans le système.";
        }

        if (lower.contains("ws_save_dum_ctca")
                && !lower.contains("saveprocesscontent")
                && !lower.contains("saveinstanceoperation")
                && !lower.contains("savereloperationscomposants")) {
            String uuid = extractUuid(msg);

            return "Le système enregistre un document DUM (CTCA)"
                    + valuePart(". uuid", uuid)
                    + ". Cette opération correspond à la sauvegarde des données liées à un document de type DUM dans le système.";
        }

        if (lower.contains("ws_save_dum_douane")
                && !lower.contains("saveprocesscontent")
                && !lower.contains("saveinstanceoperation")
                && !lower.contains("savereloperationscomposants")) {
            String uuid = extractUuid(msg);

            return "Le système enregistre un document DUM côté douane"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape concerne la gestion douanière et l’enregistrement des informations associées au document.";
        }

        if (lower.contains("importateur_dum") || (lower.contains("bpartner") && lower.contains("importateur"))) {
            String uuid = extractUuid(msg);

            if (lower.contains("0 row")) {
                return "Le système tente de récupérer l’importateur (BPartner)"
                        + valuePart(". uuid", uuid)
                        + ". Aucun importateur correspondant n’a été trouvé.";
            }

            return "Le système traite les informations de l’importateur (BPartner)"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape associe un importateur au document DUM.";
        }

        if (lower.contains("insert_exportateur_dum") || (lower.contains("bpartner") && lower.contains("exportateur"))) {
            String uuid = extractUuid(msg);

            return "Le système traite les informations de l’exportateur (BPartner)"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape permet d’associer un partenaire commercial (exportateur) au document DUM.";
        }

        if (lower.contains("ws_save_etat_chargement") && lower.contains("query:")) {
            String uuid = extractUuid(msg);
            String refGroupage = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système met à jour l’état de chargement"
                    + valuePart(". Référence groupage", refGroupage)
                    + valuePart(". uuid", uuid)
                    + ". Cette opération permet de suivre l’état d’un groupage (chargement/logistique).";
        }

        // ===== WORKS-WORKS : AJOUTS SPÉCIFIQUES 20 FICHIERS =====
        // =====================================================

        // ===== WS_SEARCH_REG_TRACABILITE : START =====
        if (lower.contains("start ws_search_reg_tracabilite")) {
            return buildExplanation(
                    "Démarrage de la recherche de traçabilité REG",
                    "Le système démarre une recherche de traçabilité complète.\n\n"
                            + "Objectif :\n"
                            + "- Retrouver l’historique d’un objet métier\n"
                            + "- Identifier les passages physiques ou logiques\n"
                            + "- Récupérer les checkpoints associés\n"
                            + "- Préparer la reconstruction du parcours\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape lance l’analyse de traçabilité d’un service, d’une unité ou d’un AMP.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_SEARCH_REG_TRACABILITE : END =====
        if (lower.contains("end ws_search_reg_tracabilite")) {
            return buildExplanation(
                    "Fin de la recherche de traçabilité REG",
                    "Le système termine la recherche de traçabilité.\n\n"
                            + "Résultat :\n"
                            + "- Les traces ont été récupérées\n"
                            + "- Les checkpoints ont été listés\n"
                            + "- Les dates d’action ont été extraites\n\n"
                            + "Interprétation métier :\n"
                            + "Le système dispose maintenant des éléments nécessaires pour expliquer le parcours de l’objet.",
                    extractExecutionTime(msg)
            );
        }

        // ===== TRACE SIZE =====
        if (lower.contains("ws_search_reg_tracabilite trace size")) {
            String traceSize = extract(msg, "trace size\\s*-+\\s*(\\d+)");

            return buildExplanation(
                    "Nombre d’événements de traçabilité détectés",
                    "Le système indique combien d’événements de traçabilité ont été trouvés.\n\n"
                            + "Détails :\n"
                            + "- Nombre d’événements : " + safeValue(traceSize, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Chaque événement représente généralement un passage, une action terrain, un scan ou une étape de suivi.\n\n"
                            + "Si la valeur est 0, aucune trace exploitable n’a été trouvée.\n"
                            + "Si la valeur est supérieure à 1, le système peut reconstruire un historique.",
                    extractExecutionTime(msg)
            );
        }

        // ===== TRCHECKPOINT NULL =====
        if (lower.contains("ws_search_reg_tracabilite trcheckpoint") && lower.contains("null")) {
            return buildExplanation(
                    "Checkpoint de traçabilité absent",
                    "Le système a détecté une ligne de traçabilité, mais le checkpoint est null.\n\n"
                            + "Interprétation métier :\n"
                            + "- Une trace existe dans les données\n"
                            + "- Mais aucune étape physique exploitable n’est renseignée\n"
                            + "- Cela peut indiquer une donnée incomplète, un passage non capturé ou une alimentation RFID manquante\n\n"
                            + "Impact :\n"
                            + "Le parcours peut être partiel ou difficile à interpréter.",
                    extractExecutionTime(msg)
            );
        }

        // ===== TRCHECKPOINT VALEUR =====
        if (lower.contains("ws_search_reg_tracabilite trcheckpoint")) {
            String checkpoint = extract(msg, "trCheckPoint\\s*-+\\s*([^\\|]+)");

            return buildExplanation(
                    "Étape de traçabilité détectée",
                    "Le système identifie une étape du parcours de l’objet.\n\n"
                            + "Détails :\n"
                            + "- Checkpoint : " + safeValue(checkpoint, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + explainCheckpoint(checkpoint) + "\n\n"
                            + "Cette information correspond généralement à une position physique ou une action réelle dans le système logistique.",
                    extractExecutionTime(msg)
            );
        }

        // ===== AMP DANS TRACABILITE =====
        if (lower.contains("ws_search_reg_tracabilite amp")) {
            String traceAmp = extract(msg, "amp\\s*-+\\s*([^\\|]+)");

            return buildExplanation(
                    "Identifiant AMP de la traçabilité",
                    "Le système indique l’AMP concerné par la trace.\n\n"
                            + "Détails :\n"
                            + "- AMP : " + safeValue(traceAmp, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "L’AMP sert d’identifiant métier pour rattacher les événements de traçabilité au bon objet ou service.",
                    extractExecutionTime(msg)
            );
        }

        // ===== DATE ACTION DANS TRACABILITE =====
        if (lower.contains("ws_search_reg_tracabilite dateaction")) {
            String actionDate = extract(msg, "dateAction\\s*-+\\s*([^\\|]+)");

            return buildExplanation(
                    "Date d’action de la traçabilité",
                    "Le système indique la date réelle de l’événement de traçabilité.\n\n"
                            + "Détails :\n"
                            + "- Date d’action : " + safeValue(actionDate, "non détectée") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette date permet de savoir quand l’objet est passé par une étape donnée.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT ENTREE COULOIR RFID =====
        if (lower.contains("entree_couloir_rfid")) {
            return buildExplanation(
                    "Entrée dans le couloir RFID",
                    "Le système détecte que l’objet est entré dans un couloir RFID.\n\n"
                            + "Interprétation métier :\n"
                            + "- Passage physique détecté automatiquement\n"
                            + "- Identification par antenne ou lecteur RFID\n"
                            + "- Début ou confirmation d’un mouvement logistique\n\n"
                            + "Cette étape indique que l’objet a été capté dans une zone de contrôle RFID.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT ENTREE PARK VISITE =====
        if (lower.contains("entree_park_visite")) {
            return buildExplanation(
                    "Entrée dans le parc de visite",
                    "Le système détecte que l’objet est entré dans la zone de visite.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet est orienté vers une zone d’inspection ou de contrôle\n"
                            + "- Cette étape peut correspondre à une vérification physique\n"
                            + "- Elle fait partie du parcours logistique avant export ou traitement final.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT ENTREE SCANNER EXPORT =====
        if (lower.contains("entree_scanner_export")) {
            return buildExplanation(
                    "Passage au scanner export",
                    "Le système détecte que l’objet est entré dans la zone scanner export.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet passe par un contrôle scanner\n"
                            + "- Cette étape peut correspondre à un contrôle sécurité ou douanier\n"
                            + "- Elle confirme une progression dans le processus export.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT SORTIE SAS EXPORT =====
        if (lower.contains("sortie_sas_export")) {
            return buildExplanation(
                    "Sortie du SAS export",
                    "Le système détecte que l’objet sort du SAS export.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet quitte une zone de transition export\n"
                            + "- Le traitement export avance vers l’étape suivante\n"
                            + "- Cette sortie peut indiquer une validation ou un passage terminé.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT ENTREE TERMINAL EXPORT =====
        if (lower.contains("entree_terminal_export")) {
            return buildExplanation(
                    "Entrée au terminal export",
                    "Le système détecte l’entrée de l’objet au terminal export.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet arrive dans la zone terminal\n"
                            + "- Il est prêt pour les étapes finales liées à l’export\n"
                            + "- Cette étape est une position importante dans le suivi logistique.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CHECKPOINT SAS IMPORT =====
        if (lower.contains("entree sas import") || lower.contains("entree_sas_import")) {
            return buildExplanation(
                    "Entrée dans le SAS import",
                    "Le système détecte l’entrée de l’objet dans le SAS import.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet entre dans une zone de transition import\n"
                            + "- Cette étape correspond au début ou à la confirmation du traitement import.",
                    extractExecutionTime(msg)
            );
        }

        if (lower.contains("sortie sas import") || lower.contains("sortie_sas_import")) {
            return buildExplanation(
                    "Sortie du SAS import",
                    "Le système détecte la sortie de l’objet du SAS import.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet quitte la zone de transition import\n"
                            + "- Le flux import continue vers une étape suivante.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_INSERT_TRACABILITE_RFID =====
        if (lower.contains("ws_insert_tracabilite_rfid")) {
            String checkpoint = extract(msg, "(ENTREE_[A-Z_]+|SORTIE_[A-Z_]+)");
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Insertion d’un événement de traçabilité RFID",
                    "Le système enregistre un événement RFID détecté sur le terrain.\n\n"
                            + "Détails :\n"
                            + "- Checkpoint : " + safeValue(checkpoint, "non détecté") + "\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette opération sauvegarde un passage physique réel de l’objet, détecté automatiquement par RFID.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_CREATE_AMPX =====
        if (lower.contains("ws_create_ampx")) {
            String noUnite = extractProcessValue(msg, "noUnite");
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Création ou mise à jour d’un AMP",
                    "Le système crée ou met à jour un objet métier AMP.\n\n"
                            + "Détails :\n"
                            + "- Unité : " + safeValue(noUnite, "non détectée") + "\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape initialise ou enrichit un objet logistique dans WORKS afin qu’il puisse être suivi, sauvegardé et rattaché à ses relations.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_SAVE_AMP DETAIL =====
        if (lower.contains("ws_save_amp")
                && !lower.contains("query:")
                && !lower.contains("searchcomposantbyroot")
                && !lower.contains("loadlistchilds")
                && !lower.contains("fire rules")
                && !lower.contains("running rules")
                && !lower.contains("saveprocesscontent")) {
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Sauvegarde d’un AMP",
                    "Le système sauvegarde les informations d’un AMP.\n\n"
                            + "Détails :\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n"
                            + "- Sauvegarde des données principales\n"
                            + "- Sauvegarde possible des relations parent/enfant\n"
                            + "- Persistance dans la base WORKS\n\n"
                            + "Interprétation métier :\n"
                            + "L’objet AMP devient exploitable pour les prochaines étapes du workflow logistique.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_FIND_TRACTION =====
        if (lower.contains("ws_findtraction") || lower.contains("ws_find_traction") || lower.contains("wsfindtraction_portail")) {
            String uuid = extractUuid(msg);
            String noUnite = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return buildExplanation(
                    "Recherche de traction logistique",
                    "Le système recherche les informations de traction associées à une unité ou à un service.\n\n"
                            + "Détails :\n"
                            + "- Unité ou référence recherchée : " + safeValue(noUnite, "non détectée") + "\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape permet de retrouver les liens de transport, par exemple camion, remorque, unité ou mouvement associé.",
                    extractExecutionTime(msg)
            );
        }

        // ===== WS_SEARCH_AMP GENERAL =====
        if (lower.contains("ws_search_amp") && !lower.contains("ws_search_amp_f")) {
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Recherche AMP",
                    "Le système recherche un AMP ou un service portuaire associé.\n\n"
                            + "Détails :\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n"
                            + "- Recherche d’un objet principal\n"
                            + "- Préparation des données utilisées par les traitements de traçabilité\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape permet d’identifier l’objet ou le service avant de récupérer ses traces, scans ou derniers points connus.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SERVICE PORTUAIRE =====
        if (lower.contains("serviceportuaire")
                && !lower.contains("end filtre serviceportuaire")
                && !lower.contains("filter code [ws_search_amp_f]")
                && !lower.contains("query:")) {
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Traitement d’un service portuaire",
                    "Le système manipule un objet métier de type ServicePortuaire.\n\n"
                            + "Détails :\n"
                            + "- uuid : " + safeValue(uuid, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Le service portuaire représente une opération logistique ou portuaire rattachée à une unité, un AMP ou un partenaire.",
                    extractExecutionTime(msg)
            );
        }

        // ===== BUSINESS OBJECT IDENTIFIED =====
        if (lower.contains("stype") || lower.contains("libelle")) {
            String type = extract(msg, "Stype\\s*=\\s*([^,\\|]+)");
            String label = extract(msg, "Libelle\\s*=\\s*([^,\\|]+)");

            return buildExplanation(
                    "Identification du type métier de l’objet",
                    "Le système identifie la catégorie métier de l’objet traité.\n\n"
                            + "Détails :\n"
                            + "- Type : " + safeValue(type, "non détecté") + "\n"
                            + "- Libellé : " + safeValue(label, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette information permet de comprendre la nature de l’objet : ensemble routier, unité de fret, service, document ou autre élément métier.",
                    extractExecutionTime(msg)
            );
        }

        // ===== FIND SERVICE START / END =====
        if (lower.contains("start find service")) {
            String service = extract(msg, "Start Find service\\s*\\*+\\s*([^\\s\\|]+)");

            return buildExplanation(
                    "Début de recherche de service",
                    "Le système démarre une recherche de service portuaire.\n\n"
                            + "Détails :\n"
                            + "- Service recherché : " + safeValue(service, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape sert à retrouver le service de base avant de reconstruire la traçabilité.",
                    extractExecutionTime(msg)
            );
        }

        if (lower.contains("end find service")) {
            String serviceCount = extract(msg, "listServices\\s*\\*+\\s*(\\d+)");

            return buildExplanation(
                    "Fin de recherche de service",
                    "Le système termine la recherche de service.\n\n"
                            + "Détails :\n"
                            + "- Nombre de services trouvés : " + safeValue(serviceCount, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Si le nombre est supérieur à zéro, le système a trouvé au moins un service exploitable pour la suite du traitement.",
                    extractExecutionTime(msg)
            );
        }

        // ===== END FILTRE SERVICEPORTUAIRE =====
        if (lower.contains("end filtre serviceportuaire")) {
            String serviceSize = extract(msg, "size\\s*::\\s*(\\d+)");

            return buildExplanation(
                    "Fin du filtre ServicePortuaire",
                    "Le système termine le filtrage des services portuaires.\n\n"
                            + "Détails :\n"
                            + "- Nombre de services retenus : " + safeValue(serviceSize, "non détecté") + "\n\n"
                            + "Interprétation métier :\n"
                            + "Cette étape indique combien de services correspondent aux critères métier.",
                    extractExecutionTime(msg)
            );
        }

        // ===== TRACE VIDE / TRACE PARTIELLE =====
        if (lower.contains("trace size") && lower.contains("0")) {
            return buildExplanation(
                    "Aucune trace exploitable trouvée",
                    "Le système n’a trouvé aucun événement de traçabilité exploitable.\n\n"
                            + "Interprétation métier :\n"
                            + "- L’objet existe peut-être dans le système\n"
                            + "- Mais aucun passage terrain ou checkpoint n’est disponible\n"
                            + "- Cela peut être normal ou indiquer un manque de données.",
                    extractExecutionTime(msg)
            );
        }


        // ===== PROCESS FILTER / WARNINGS =====
        if (lower.contains("ws_search_reg_tracabilite")
                && !lower.contains("saveprocesscontent")
                && !lower.contains("aucune")
                && !lower.contains("fire rules")
                && !lower.contains("running rules")
                && !lower.contains("start ws_search_reg_tracabilite")
                && !lower.contains("end ws_search_reg_tracabilite")
                && !lower.contains("trace size")
                && !lower.contains("trcheckpoint")
                && !lower.contains("dateaction")
                && !lower.contains(" amp")
                && !lower.contains("row fetched")
                && !lower.contains("query:")) {
            String uuid = extractUuid(msg);

            return "Le système effectue une recherche de tracabilité REG"
                    + valuePart(". uuid", uuid)
                    + ". Cette opération récupère les informations liées aux enregistrements REG dans le système.";
        }

        if (lower.contains("aucune règle")) {
            String uuid = extractUuid(msg);
            String process = extractBracket(msg, "PROCESS_NAME");
            String task = extractBracket(msg, "TASK_NAME");
            String action = extractBracket(msg, "ACTION_NAME");

            return buildExplanation(
                    "Aucune règle métier appliquée",
                    "Le système indique qu’aucune règle métier n’a été exécutée.\n\n"
                            + "Contexte détecté :"
                            + valuePart("\n- Process", process)
                            + valuePart("\n- Tâche", task)
                            + valuePart("\n- Action", action)
                            + valuePart("\n- uuid", uuid)
                            + "\n\nInterprétations possibles :\n"
                            + "- Aucun traitement spécifique nécessaire\n"
                            + "- Cas simple sans logique métier\n"
                            + "- Ou configuration incomplète\n\n"
                            + "Impact :\n"
                            + "Le traitement s’est limité à des opérations standards.",
                    extractExecutionTime(msg)
            );
        }

        // ===== MULTITHREADING DEFAULT ARGS =====
        if (lower.contains("multithreading_default_args")) {
            return buildExplanation(
                    "Exécution en multi-threading",
                    "Le système active le traitement en parallèle (multi-threading).\n\n"
                            + "Détails :\n"
                            + "- Découpage des données en partitions\n"
                            + "- Exécution sur plusieurs threads\n"
                            + "- Optimisation du temps de traitement\n\n"
                            + "Interprétation :\n"
                            + "Le système traite un volume de données potentiellement important et utilise plusieurs threads pour accélérer l’exécution.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SEARCH BY BUSINESS IDENTIFIER =====
        if (lower.contains("string_value")
                && lower.matches(".*det_comp_fils1\\.string_value\\s*=\\s*(dg[a-z0-9_\\-]*|r[a-z0-9_\\-]*).*")) {
            String identifier = extract(msg, "det_comp_fils1\\.string_value\\s*=\\s*([^\\s\\|]+)");

            return buildExplanation(
                    "Recherche par identifiant métier",
                    "Le système filtre les données à partir d’un identifiant spécifique.\n\n"
                            + "Détails :\n"
                            + "- Identifiant unique détecté" + valuePart(" :", identifier) + "\n"
                            + "- Recherche ciblée sur un objet métier précis\n\n"
                            + "Interprétation métier :\n"
                            + "Cette recherche permet de retrouver un objet précis dans le système, par exemple un véhicule, une unité de transport, un conteneur ou un service.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SAVE AMP DATA =====

        // ===== SEARCH ROOT START / REQUEST EXECUTION =====
        if (lower.contains("searchcomposantbyroot start execute")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String applyNewSearch = extractBracket(msg, "applyNewSearchByRoot");

            return "Le système démarre une recherche métier par racine"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + valuePart(". applyNewSearchByRoot", applyNewSearch)
                    + ". Cette étape indique le début du traitement de recherche avant l’exécution SQL.";
        }

        if (lower.contains("begin executing request")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système commence l’exécution réelle de la requête associée"
                    + valuePart(" au filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape correspond au lancement de l’accès aux données en base.";
        }

        if (lower.contains("executed request finished")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "La requête associée au filtre"
                    + valuePart("", filter)
                    + " est terminée"
                    + valuePart(". uuid", uuid)
                    + ". Le résultat pourra ensuite être chargé, transformé ou affiché dans les étapes suivantes.";
        }

        if (lower.contains("preparesearchbyroot") && lower.contains("query select")) {
            String duration = extractDuration(msg);
            String rows = extract(msg, "\\|\\s*(\\d+)\\s*row");
            String clazz = extract(msg, "className\\s+([^,\\|]+)");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            if ("0".equals(rows)) {
                return "La requête préparatoire de recherche a été exécutée"
                        + valuePart(" pour le filtre", filter)
                        + valuePart(" sur la classe", clazz)
                        + valuePart(". uuid", uuid)
                        + ". Résultat : 0 ligne trouvée. Cela indique qu’aucun objet métier ne correspond aux critères de recherche."
                        + durationPart(duration);
            }

            return "La requête préparatoire de recherche a été exécutée"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(" sur la classe", clazz)
                    + valuePart(". Résultat", rows, " ligne(s)")
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        // ===== SEARCH COMPOSANT BY ROOT : GENERIC FALLBACK =====
        if (lower.contains("searchcomposantbyroot")
                && !lower.contains("start execute")
                && !lower.contains("preparesearchbyroot")
                && !lower.contains("query:")
                && !lower.contains("filter code [ws_search_reg]")
                && !lower.contains("filter code [lastpoint_reg]")
                && !lower.contains("filter code [scan_reg]")
                && !lower.contains("filter code [ebook_reg]")
                && !lower.contains("filter code [find_tracabilite_ws_reg]")) {
            return "Le système effectue une recherche de composants à partir d’un élément racine. "
                    + "Les résultats sont filtrés selon des critères métier et retournés pour traitement.";
        }

        // ===== MULTITHREADING =====
        if (lower.contains("using multithreading") && !lower.contains("start dosearch")) {
            String partitionSize = extractBracket(msg, "partitionSize");
            String nbrThreadPool = extractBracket(msg, "nbrThreadPool");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système active le traitement multi-thread pour accélérer la recherche"
                    + valuePart(". Taille de partition", partitionSize)
                    + valuePart(". Nombre de threads", nbrThreadPool)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape indique que les données seront traitées en parallèle.";
        }

        if (lower.contains("ignore multithreading")) {
            String ids = extract(msg, "for\\s*\\[(\\d+)]\\s*ids");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système ignore le multi-threading"
                    + valuePart(" car le nombre d’identifiants à traiter est", ids)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Le traitement restera donc simple ou séquentiel.";
        }





        // ===== RULES =====
        if (lower.contains("no rule found for this params")) {
            String task = extract(msg, "taskName=([^,}\\]]+)");
            String transition = extract(msg, "transition=([^,}\\]]+)");
            String uuid = extractUuid(msg);
            String planId = extract(msg, "planId=([^,}\\]]+)");
            String dateValeur = extract(msg, "dateValeur=([^,}\\]]+)");

            return "Le moteur de règles a cherché une règle métier"
                    + valuePart(" pour la tâche", task)
                    + valuePart(" avec la transition", transition)
                    + valuePart(" pour le planId", planId)
                    + valuePart(" à la date métier", dateValeur)
                    + valuePart(" (uuid", uuid, ")")
                    + ", mais aucune règle applicable n’a été trouvée. "
                    + "Cela indique probablement une configuration métier manquante pour cette tâche, cette transition ou ce contexte.";
        }

        if (lower.contains("start fire rules")) {
            String uuid = extractUuid(msg);
            String process = extractBracket(msg, "processName");
            String task = extractBracket(msg, "taskName");
            String action = extractBracket(msg, "actionName");

            return "Le système démarre l’exécution des règles métier"
                    + valuePart(" pour le process", process)
                    + valuePart(", la tâche", task)
                    + valuePart(" et l’action", action)
                    + valuePart(" (uuid", uuid, ")")
                    + ".";
        }

        if (lower.contains("end fire rules")) {
            String uuid = extractUuid(msg);
            String process = extractBracket(msg, "processName");
            String task = extractBracket(msg, "taskName");
            String action = extractBracket(msg, "actionName");

            return "Le système termine l’exécution des règles métier"
                    + valuePart(" pour le process", process)
                    + valuePart(", la tâche", task)
                    + valuePart(" et l’action", action)
                    + valuePart(" (uuid", uuid, ")")
                    + ".";
        }

        if (lower.contains("aucune") && lower.contains("transition")) {
            String process = extractBracket(msg, "PROCESS_NAME");
            String task = extractBracket(msg, "TASK_NAME");
            String action = extractBracket(msg, "ACTION_NAME");

            return "Aucune règle métier n’a été affectée à cette transition"
                    + valuePart(" pour le process", process)
                    + valuePart(", la tâche", task)
                    + valuePart(" et l’action", action)
                    + ". Le workflow peut continuer, mais sans logique métier spécifique. "
                    + "Cela indique probablement une règle absente ou une configuration incomplète.";
        }

        if (lower.contains("running rules")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système a exécuté les règles métier"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + valuePart(". Identifiant source", p.get(4))
                    + valuePart(", identifiant cible", p.get(5))
                    + durationPart(duration);
        }

        if (lower.contains("start rule")) {
            String rule = extract(msg, "Start Rule\\s+([^_\\|]+)");
            String size = extract(msg, "SIZE ALL\\s*_+(\\d+)");

            return "Le moteur démarre l’exécution de la règle"
                    + valuePart("", rule)
                    + valuePart(". Nombre d’éléments ou règles candidats à vérifier", size)
                    + ".";
        }

        if (lower.contains("size checked")) {
            String rule = extract(msg, "Rule\\s+([^_\\|]+)");
            String checked = extract(msg, "SIZE CHECKED\\s*_+(\\d+)");

            return "Le moteur indique le nombre d’éléments réellement vérifiés pour la règle"
                    + valuePart("", rule)
                    + valuePart(". Nombre vérifié", checked)
                    + ".";
        }

        if (lower.contains("end rule")) {
            String rule = extract(msg, "End Rule\\s+([^_\\|]+)");

            return "Le moteur termine l’exécution de la règle"
                    + valuePart("", rule)
                    + ".";
        }

        // ===== SAVELOADLOGFILE / TRACE SAVE LOGGER DETAILS =====
        if (lower.contains("start dosearch") && lower.contains("using multithreading")) {
            String elements = extract(msg, "for\\s+(\\d+)\\s+element");
            String thread = extractThread(msg);
            String progress = extract(msg, "-->\\s*([^,\\|]+)");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système démarre une recherche doSearch en mode multi-thread"
                    + valuePart(". Nombre d’éléments", elements)
                    + valuePart(". Thread", thread)
                    + valuePart(". Progression", progress)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Le traitement est découpé pour gérer un volume important.";
        }

        if (lower.contains("start dosearch")) {
            String elements = extract(msg, "Start doSearch\\s+(?:\\(using multiThreading\\)\\s*)?for\\s+(\\d+)\\s+element");
            String thread = extractThread(msg);
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système démarre une phase de recherche interne doSearch"
                    + valuePart(". Nombre d’éléments à traiter", elements)
                    + valuePart(". Thread", thread)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape charge les objets liés au résultat racine avant de compléter les données affichées ou exploitées.";
        }

        if (lower.contains("using tempquery") && lower.contains("generatesqlquery")) {
            String strategy = extractBracketAfter(msg, "using");
            String uuid = extractUuid(msg);
            String tx = extractTransactionId(msg);
            String sizeRootIds = extract(msg, "sizeRootIds\\s*=\\s*(\\d+)");
            String maxTempValues = extract(msg, "maxTempValues\\s*=\\s*(\\d+)");

            return "Le système génère une requête temporaire pour organiser le chargement des données liées"
                    + valuePart(". Stratégie", strategy)
                    + valuePart(". Nombre d’identifiants racines", sizeRootIds)
                    + valuePart(". Limite temporaire", maxTempValues)
                    + valuePart(". transactionId", tx)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape sert à éviter une requête trop lourde et à découper le chargement.";
        }

        if (lower.contains("savetempcomposantsloaded")) {
            String strategy = extractBracketAfter(msg, "using");
            String uuid = extractUuid(msg);
            String tx = extractTransactionId(msg);
            String duration = extractDuration(msg);

            return "Le système sauvegarde temporairement les composants chargés"
                    + valuePart(". Stratégie", strategy)
                    + valuePart(". transactionId", tx)
                    + valuePart(". uuid", uuid)
                    + ". Ces données temporaires seront utilisées par les requêtes suivantes pour compléter le résultat."
                    + durationPart(duration);
        }

        if (lower.contains("deletetempcomposantsloaded")) {
            String uuid = extractUuid(msg);
            String tx = extractTransactionId(msg);
            String duration = extractDuration(msg);

            return "Le système supprime les composants temporaires utilisés pendant la recherche"
                    + valuePart(". transactionId", tx)
                    + valuePart(". uuid", uuid)
                    + ". Cela nettoie les données intermédiaires après utilisation."
                    + durationPart(duration);
        }

        if (lower.contains("searchcomposantbyroot ==> preparesearchbyroot")) {
            String clazz = extractBracket(msg, "className");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système prépare la recherche racine searchComposantByRoot"
                    + valuePart(". Classe métier recherchée", clazz)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape prépare la requête qui retrouvera les objets métiers principaux.";
        }

        if (lower.contains("query:") && lower.contains("det_comp_fils0.cle = noservice")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String noService = extract(msg, "string_value\\s*=\\s*([^\\s\\|]+)");

            return "Le système exécute une requête SQL pour retrouver un ServicePortuaire par numéro de service"
                    + valuePart(". noService recherché", noService)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette requête vérifie l’existence du service dans les composants WORKS actifs.";
        }

        if (lower.contains("wvnoserviceegal")) {
            String value = extractBracketAfter(msg, "wvnoServiceegal");

            return "Le système applique un critère d’égalité sur le champ noService"
                    + valuePart(". noService attendu", value)
                    + ". Ce paramètre limite la recherche au service portuaire demandé.";
        }

        if (lower.contains("wnoservice")) {
            String value = extractBracketAfter(msg, "wnoService");

            return "Le système indique que le champ métier utilisé pour la recherche est noService"
                    + valuePart(". Champ", value)
                    + ".";
        }

        if (lower.matches(".*\\bfv\\d+\\s*=\\[[^\\]]+].*")) {
            String value = extract(msg, "\\bfv(\\d+)\\s*=\\[([^\\]]+)]");

            return "Le système applique un filtre technique sur la classe de composant"
                    + valuePart(". Paramètre", value)
                    + ". Cette valeur sert à limiter la recherche au bon type d’objet métier.";
        }

        if (lower.contains("preparedstatement disabled=false")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système confirme que les PreparedStatements sont activés"
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". La requête peut donc être exécutée de manière paramétrée.";
        }

        if (lower.contains("searchattributeslist") && lower.contains("start loading data")) {
            String tx = extractTransactionId(msg);
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système commence le chargement des attributs détaillés des objets trouvés"
                    + valuePart(". transactionId", tx)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Après avoir trouvé les identifiants racines, il récupère maintenant les champs métier associés.";
        }

        if (lower.contains("searchattributeslist") && lower.contains("[query]")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système exécute la requête de récupération des attributs métier"
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette étape complète les objets trouvés avec leurs valeurs détaillées."
                    + durationPart(duration);
        }

        if (lower.contains("searchtostringmapping") && lower.contains("[query]")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système récupère le mapping d’affichage textuel des objets"
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Ce mapping sert à transformer les objets techniques en valeurs lisibles."
                    + durationPart(duration);
        }
        // ===== LOADLOGFILE / PROCESS FILTER DETAILS =====
        if (lower.contains("filter code [ebook_reg]") && lower.contains("className [Ebook]".toLowerCase(Locale.ROOT))) {
            String uuid = extractUuid(msg);

            return "Le système prépare une recherche d’Ebook dans le filtre EBOOK_REG"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape vérifie si un e-book est lié à l’AMP ou au service traité.";
        }

        if (lower.contains("filter code [ebook_reg]") && lower.contains("0 row")) {
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "La recherche EBOOK_REG est terminée sans résultat"
                    + valuePart(". uuid", uuid)
                    + ". Aucun e-book correspondant n’a été retrouvé pour ce contexte."
                    + durationPart(duration);
        }

        if (lower.contains("filter code [scan_reg]") && lower.contains("classname scan")) {
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système recherche les informations de scan via le filtre SCAN_REG"
                    + valuePart(". uuid", uuid)
                    + ". Cette étape sert à retrouver un passage scanner ou une lecture liée à l’unité."
                    + durationPart(duration);
        }

        // ===== SCAN_REG GENERAL SAFE =====
        if (lower.contains("scan_reg")
                && !lower.contains("query:")
                && !lower.contains("row fetched")
                && !lower.contains("preparesearchbyroot")
                && !lower.contains("fire rules")
                && !lower.contains("running rules")
                && !lower.contains("saveprocesscontent")) {
            return buildExplanation(
                    "Scan basé sur des critères métier et temporels",
                    "Le système exécute une recherche de type scan.\n\n"
                            + "Détails :\n"
                            + "- Filtrage basé sur un identifiant métier, souvent AMP\n"
                            + "- Filtrage basé sur une date, souvent dateMessage\n"
                            + "- Requête SQL combinant plusieurs conditions\n\n"
                            + "Interprétation métier :\n"
                            + "Le système cherche des éléments spécifiques en fonction d’un identifiant et d’une période donnée.",
                    extractExecutionTime(msg)
            );
        }

        if (lower.contains("filter code [find_tracabilite_ws_reg]")) {
            String uuid = extractUuid(msg);
            String rows = extract(msg, "\\|\\s*(\\d+)\\s*row");
            String duration = extractDuration(msg);

            return "Le système recherche les événements de traçabilité via Find_Tracabilite_WS_REG"
                    + valuePart(". Résultat", rows, " ligne(s)")
                    + valuePart(". uuid", uuid)
                    + ". Cette étape récupère l’historique de passage déjà enregistré pour le service ou l’unité."
                    + durationPart(duration);
        }


        if (lower.contains("whaschanged")) {
            String field = extractBracketAfter(msg, "whasChanged");

            return "Le système ajoute le critère hasChanged à la recherche"
                    + valuePart(". Champ", field)
                    + ". Cela permet d’exclure ou sélectionner les services selon leur état de modification.";
        }

        if (lower.contains("wse")) {
            String field = extractBracketAfter(msg, "wse");

            return "Le système ajoute le critère SE à la recherche"
                    + valuePart(". Champ", field)
                    + ". Ce critère sert à exclure certains états de service, par exemple SE_CL.";
        }

        if (lower.contains("wvnomservicein")) {
            String values = extractBracketAfter(msg, "wvnomServicein");

            return "Le système filtre les services par type de service"
                    + valuePart(". Valeurs acceptées", values)
                    + ". Ici, la recherche garde généralement les services AMPE et AMPI.";
        }

        if (lower.contains("wnomservice")) {
            String field = extractBracketAfter(msg, "wnomService");

            return "Le système indique que le champ métier utilisé pour le type de service est"
                    + valuePart("", field)
                    + ".";
        }

        if (lower.contains("wvampeegal")) {
            String value = extractBracketAfter(msg, "wvampeegal");

            return "Le système applique un critère d’égalité sur l’AMPE"
                    + valuePart(". AMPE recherchée", value)
                    + ". Cette recherche sert à vérifier l’existence d’un Ebook lié à cette référence.";
        }

        if (lower.contains("wampe")) {
            String field = extractBracketAfter(msg, "wampe");

            return "Le système indique que le champ métier utilisé pour la recherche Ebook est"
                    + valuePart("", field)
                    + ".";
        }

        if (lower.contains("automaticprocessing") && lower.contains("fireRules".toLowerCase(Locale.ROOT))) {
            String service = extract(msg, "\\[\\[([^\\]]+)]]");

            return "Le système lance automatiquement l’exécution des règles"
                    + valuePart(". Service concerné", service)
                    + ". Ce traitement est déclenché sans action manuelle directe.";
        }

        // ===== AUTOMATIC RULE ENGINE =====
        if ((lower.contains("automaticprocessing") || lower.contains("firerules"))
                && !lower.contains("start fire rules")
                && !lower.contains("end fire rules")
                && !lower.contains("running rules")
                && !lower.contains("no rule found")
                && !lower.contains("aucune règle")) {
            return buildExplanation(
                    "Exécution automatique des règles métier",
                    "Le système déclenche automatiquement des règles métier.\n\n"
                            + "Détails :\n"
                            + "- Traitement automatique\n"
                            + "- Aucune intervention utilisateur\n"
                            + "- Déclenché par workflow\n\n"
                            + "Interprétation métier :\n"
                            + "Le système applique des règles pour automatiser le traitement des données.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SEARCH TRACABILITE BY SERVICE =====
        if (lower.contains("tracabilite") && lower.contains("noservice")) {
            return buildExplanation(
                    "Recherche de traçabilité par identifiant service",
                    "Le système recherche les données de traçabilité associées à un service.\n\n"
                            + "Détails :\n"
                            + "- Utilisation d’un identifiant unique (noService)\n"
                            + "- Accès aux historiques associés\n\n"
                            + "Interprétation métier :\n"
                            + "Permet de retracer les actions effectuées sur un service.\n\n"
                            + "Exemple :\n"
                            + "Historique des opérations d’un service spécifique.",
                    extractExecutionTime(msg)
            );
        }

        // ===== CLEAN BEFORE SAVE =====
        if (lower.contains("removeobjectstodelete")) {
            return buildExplanation(
                    "Nettoyage des données avant sauvegarde",
                    "Le système supprime des éléments avant la sauvegarde.\n\n"
                            + "Interprétation :\n"
                            + "- Évite les doublons\n"
                            + "- Nettoie les données obsolètes\n"
                            + "- Assure la cohérence\n\n"
                            + "Étape typique avant insertion ou mise à jour.",
                    extractExecutionTime(msg)
            );
        }



        // ===== RFID =====
        if (lower.contains("rfid")) {
            return buildExplanation(
                    "Recherche basée sur RFID",
                    "Le système traite une requête liée à un tag RFID.\n\n"
                            + "Détails :\n"
                            + "- Identification via noTagRFID\n"
                            + "- Filtrage avancé (statut, date, état)\n"
                            + "- Recherche de services associés\n\n"
                            + "Interprétation métier :\n"
                            + "Le système localise et analyse des éléments liés à un tag RFID.",
                    extractExecutionTime(msg)
            );
        }


        // ===== PERFORMANCE ALERT =====
        long time = extractExecutionTimeValue(msg);
        if (time > 3000) {
            return buildExplanation(
                    "Temps d'exécution élevé détecté",
                    "Le traitement a pris un temps anormalement élevé.\n\n"
                            + "Analyse :\n"
                            + "- Requête complexe ou volumineuse\n"
                            + "- Plusieurs jointures ou filtres\n"
                            + "- Possible surcharge base de données\n\n"
                            + "Impact :\n"
                            + "Peut ralentir le système global.\n\n"
                            + "Recommandation :\n"
                            + "Optimisation requête ou indexation.",
                    extractExecutionTime(msg)
            );
        }

        // ===== PIPELINE LOADLISTCHILDS COMPLET =====
        if (lower.contains("loadlistchilds") && lower.contains("query b1")) {
            return buildExplanation(
                    "Pipeline de chargement de données (multi-étapes)",
                    "Le système exécute un pipeline complexe de chargement de données en plusieurs étapes.\n\n"
                            + "Étapes détectées :\n"
                            + "- Query B1 : récupération initiale des données\n"
                            + "- InsertTemp : stockage temporaire intermédiaire\n"
                            + "- Query B2 : enrichissement ou jointure complémentaire\n"
                            + "- Filling data : transformation finale des données\n\n"
                            + "Interprétation métier :\n"
                            + "Le système construit progressivement les données finales à partir de plusieurs requêtes et transformations.",
                    extractExecutionTime(msg)
            );
        }

        // ===== LOAD LIST CHILDS =====
        if (lower.contains("loadlistchilds")) {
            return explainLoadListChilds(msg, lower);
        }

        // ===== DYNAMIC QUERY PARAMETERS =====
        if (isDynamicQueryParameter(lower)) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            DynamicParam param = parseDynamicParam(msg);

            return "Paramètre de recherche détecté"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". " + param.explanation;
        }
        // ===== PREPARED STATEMENT =====
        if (lower.contains("preparedstatement disabled")) {
            String disabled = extract(msg, "PreparedStatement\\s+disabled\\s*=\\s*([^\\s\\|]+)");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système indique l’état d’utilisation des PreparedStatements"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + valuePart(". disabled", disabled)
                    + ". Si la valeur est false, l’exécution préparée ou paramétrée reste disponible.";
        }
        // ===== FETCHING FILTER =====
        if (lower.contains("fetching filter")) {
            String filter = extractBracket(msg, "fetching filter");
            String bean = extract(msg, "bean:\\s*(.+)$");

            return "Le système récupère la définition ou la configuration du filtre"
                    + valuePart("", filter)
                    + valuePart(". Structure interne", bean)
                    + ". Cela indique que le filtre est chargé depuis le cache ou une configuration applicative.";
        }

        // ===== PERFORMANCE LENTE =====
        if (extractExecutionTimeValue(msg) > 5000) {
            return buildExplanation(
                    "Temps d’exécution élevé détecté",
                    "L’opération a pris un temps anormalement élevé.\n\n"
                            + "Interprétation :\n"
                            + "- Volume de données important\n"
                            + "- Requête complexe\n"
                            + "- Ou problème de performance\n\n"
                            + "Impact :\n"
                            + "Cela peut ralentir le système et nécessite une analyse.",
                    extractExecutionTime(msg)
            );
        }

        // ===== PERFORMANCE NIVEAU =====
        long executionTime = extractExecutionTimeValue(msg);
        if (executionTime > 1000 && executionTime <= 3000) {
            return buildExplanation(
                    "Temps d’exécution modéré",
                    "Le traitement a pris un temps notable.\n\n"
                            + "Interprétation :\n"
                            + "- Volume de données non négligeable\n"
                            + "- Requête ou traitement relativement complexe\n\n"
                            + "À surveiller si cela se répète.",
                    extractExecutionTime(msg)
            );
        }

        // ===== SEARCH / SQL =====
        if (lower.contains("searchcomposantbyroot end execute")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String rows = extract(msg, "\\[(\\d+)\\]\\s*row\\s*fetched");

            if ("0".equals(rows)) {
                return "La recherche métier par racine"
                        + valuePart(" sur le filtre", filter)
                        + valuePart(" (uuid", uuid, ")")
                        + " s’est terminée sans aucun résultat. "
                        + "Cela peut indiquer des critères trop restrictifs ou l’absence de données correspondantes.";
            }

            return "La recherche métier par racine"
                    + valuePart(" sur le filtre", filter)
                    + valuePart(" (uuid", uuid, ")")
                    + " s’est terminée avec " + safeValue(rows, "un nombre inconnu de") + " résultat(s).";
        }

        if (lower.contains("global searchcomposantbyroot took")) {
            String duration = extractDuration(msg);
            String rows = extract(msg, "\\|\\s*(\\d+)\\s*row");
            String clazz = extract(msg, "className\\s+([^,\\|]+)");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "La recherche globale par racine est terminée"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(" sur la classe", clazz)
                    + valuePart(". Résultat trouvé", rows, " ligne(s)")
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("end dosearch.finally")) {
            String thread = extractThread(msg);
            String filter = extractBracket(msg, "filter code");
            String transactionId = extractTransactionId(msg);
            String uuid = extractUuid(msg);

            return "La phase finale de recherche est terminée"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ".";
        }

        if (lower.contains("preparesearchbyroot")) {
            String clazz = extractBracket(msg, "className");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système prépare une recherche métier par racine"
                    + valuePart(" sur le type d’objet", clazz)
                    + valuePart(" avec le filtre", filter)
                    + valuePart(" et l’uuid", uuid)
                    + ".";
        }

        if (lower.contains("query:")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système exécute une requête SQL"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(" avec l’uuid", uuid)
                    + ". Cette ligne correspond à l’accès aux données en base.";
        }

        if (lower.contains("using tempquery")) {
            String uuid = extractUuid(msg);
            String transactionId = extractTransactionId(msg);
            String strategy = extractBracketAfter(msg, "using");
            String sizeRootIds = extract(msg, "sizeRootIds\\s*=\\s*(\\d+)");
            String maxTempValues = extract(msg, "maxTempValues\\s*=\\s*(\\d+)");

            return "Le système prépare une requête temporaire pour accélérer ou organiser le chargement des données"
                    + valuePart(". Stratégie utilisée", strategy)
                    + valuePart(". Nombre d’identifiants racines", sizeRootIds)
                    + valuePart(". Limite de valeurs temporaires", maxTempValues)
                    + valuePart(". transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ".";
        }

        if (lower.contains("savetempcomposantsloaded")) {
            String uuid = extractUuid(msg);
            String transactionId = extractTransactionId(msg);
            String elements = extract(msg, "for\\s*\\[(\\d+)]\\s*element");
            String duration = extractDuration(msg);

            return "Le système sauvegarde temporairement les composants chargés afin de continuer le traitement"
                    + valuePart(". Nombre d’éléments", elements)
                    + valuePart(". transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("deletetempcomposantsloaded")) {
            String uuid = extractUuid(msg);
            String transactionId = extractTransactionId(msg);
            String duration = extractDuration(msg);

            return "Le système supprime les composants temporaires utilisés pendant le chargement"
                    + valuePart(". transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("searchattributeslist") && lower.contains("start loading data")) {
            String filter = extractBracket(msg, "filter code");
            String transactionId = extractTransactionId(msg);
            String uuid = extractUuid(msg);

            return "Le système démarre le chargement des attributs métier"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ".";
        }

        if (lower.contains("searchattributeslist") && lower.contains("[query]")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système exécute la requête de chargement des attributs métier"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("searchcontroleinstances") && lower.contains("start loading data")) {
            String filter = extractBracket(msg, "filter code");
            String transactionId = extractTransactionId(msg);
            String uuid = extractUuid(msg);

            return "Le système démarre le chargement des instances de contrôle"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ".";
        }

        if (lower.contains("searchcontroleinstances") && lower.contains("[query]")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système exécute la requête de chargement des instances de contrôle"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("searchtostringmapping") && lower.contains("[query]")) {
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système charge le mapping textuel utilisé pour afficher les objets métier sous forme lisible"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("0 row")) {
            String clazz = extractAfter(msg, "className ");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return buildExplanation(
                    "Aucun résultat trouvé",
                    "La requête exécutée n’a retourné aucun résultat.\n\n"
                            + "Contexte :"
                            + valuePart("\n- Type d’objet", clazz)
                            + valuePart("\n- Filtre", filter)
                            + valuePart("\n- uuid", uuid)
                            + "\n\nInterprétation :\n"
                            + "- Aucun élément ne correspond aux critères\n"
                            + "- La donnée peut être absente ou incorrecte\n\n"
                            + "Impact :\n"
                            + "Cela peut indiquer un cas normal ou un problème métier.",
                    extractExecutionTime(msg)
            );
        }

        // ===== VARIATION DES RESULTATS =====
        if (lower.matches(".*\\b\\d+\\s*row\\b.*") || lower.matches(".*\\b\\d+\\s*size\\b.*") || lower.matches(".*\\bsize\\s*[=:]?\\s*\\d+.*")) {
            return buildExplanation(
                    "Variation du nombre de résultats",
                    "Le système traite un nombre variable de résultats.\n\n"
                            + "Interprétation :\n"
                            + "- Le volume de données dépend de la requête\n"
                            + "- Il peut varier selon les critères métier\n"
                            + "- Il peut impacter les performances\n\n"
                            + "Cela reflète un comportement dynamique du système.",
                    extractExecutionTime(msg)
            );
        }

        if (lower.matches(".*\\b[wfv][a-z0-9_]*\\s*=\\[[^\\]]+].*")) {
            String param = extract(msg, "\\b([A-Za-z0-9_]+)\\s*=\\[([^\\]]+)]");
            return "Un paramètre de recherche a été détecté : " + safeValue(param, msg) + ". "
                    + "Ce paramètre est utilisé pour filtrer les données recherchées.";
        }

        // ===== PERFORMANCE / MEMORY =====
        if (lower.contains("memory usage")) {
            String memory = extractLastNumber(msg);
            String context = extractBracketAfter(msg, "memory usage");
            String thread = extractThread(msg);
            String filter = extractBracket(msg, "filter code");
            String transactionId = extractTransactionId(msg);
            String uuid = extractUuid(msg);

            return "Une mesure mémoire a été enregistrée pendant le traitement"
                    + valuePart(". Contexte", context)
                    + valuePart(". Filtre", filter)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ". Mémoire observée : " + safeValue(memory, "valeur non détectée") + " Mo.";
        }

        // ===== ACTION / SAVE / PROCESS =====
        if (lower.contains("load contexttofile")) {
            PipeParts p = pipeParts(msg);
            String uuid = extractBracketAfter(msg, "");
            String duration = extractDuration(msg);

            return "Le système charge le contexte du workflow depuis un fichier ou une zone de contexte"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("saveprocesscontent")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système sauvegarde le contenu du process après l’exécution de l’action métier"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + valuePart(". Identifiant source", p.get(4))
                    + valuePart(", identifiant cible", p.get(5))
                    + durationPart(duration);
        }

        if (lower.contains("removeobjectstodelete")) {
            String uuid = extractUuid(msg);
            String keys = extractBracket(msg, "keys");
            String duration = extractDuration(msg);

            return "Le système vérifie les objets marqués pour suppression"
                    + valuePart(" pour l’uuid", uuid)
                    + valuePart(". Clés concernées", keys)
                    + ". Si la liste des clés est vide, aucun objet précis n’a été supprimé."
                    + durationPart(duration);
        }

        if (lower.contains("total time save")) {
            String id = extract(msg, "SAVE\\s*\\[([^\\]]+)]");
            String key = extractBracket(msg, "key");
            String duration = extract(msg, ";\\s*(\\d+)\\s*\\(ms\\)");

            return "La sauvegarde de l’objet métier est terminée"
                    + valuePart(" pour le type", key)
                    + valuePart(" avec l’identifiant", id)
                    + ". Durée totale de sauvegarde : " + safeValue(duration, "valeur inconnue") + " ms.";
        }

        if (lower.contains("savereloperationscomposants")) {
            String duration = extractDuration(msg);
            return "Exécution interne du moteur de sauvegarde. "
                    + "Le système enregistre les relations entre les différents composants métier. "
                    + "Cette étape complète la sauvegarde de l’objet principal en persistant ses liens avec les autres objets."
                    + durationPart(duration);
        }

        if (lower.contains("saveinstanceoperation")) {
            String duration = extractDuration(msg);
            return "Exécution interne du moteur de sauvegarde. "
                    + "Le système sauvegarde l’objet principal de l’opération métier en base de données. "
                    + "Cette étape persiste les données principales avant ou avec les relations associées."
                    + durationPart(duration);
        }

        if (lower.contains("validateattributesoperation")) {
            String duration = extractDuration(msg);
            return "Le système valide les attributs de l’objet métier avant la sauvegarde"
                    + durationPart(duration);
        }

        if (lower.contains("validateoperation")) {
            String duration = extractDuration(msg);
            return "Le système valide l’opération métier globale avant de continuer la sauvegarde"
                    + durationPart(duration);
        }

        if (lower.contains("saveoperations")) {
            String duration = extractDuration(msg);
            return "Le système sauvegarde les opérations métier associées au traitement"
                    + durationPart(duration);
        }

        if (lower.contains("persist operation")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système persiste l’opération métier en base"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + valuePart(". Identifiant source", p.get(4))
                    + valuePart(", identifiant cible", p.get(5))
                    + durationPart(duration);
        }

        if (lower.contains("save documents")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système sauvegarde les documents liés au workflow"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + durationPart(duration);
        }

        if (lower.contains("save comments")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système sauvegarde les commentaires liés au workflow"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + durationPart(duration);
        }

        if (lower.contains("save anomalies")) {
            PipeParts p = pipeParts(msg);
            String duration = extractDuration(msg);

            return "Le système sauvegarde les anomalies détectées ou associées au workflow"
                    + valuePart(" pour le process", p.get(1))
                    + valuePart(", la tâche", p.get(2))
                    + valuePart(" et l’action", p.get(3))
                    + durationPart(duration);
        }

        if (lower.contains("start saveorupdate")) {
            return "Début d’une opération de sauvegarde ou de mise à jour d’un objet métier.";
        }

        if (lower.contains("end saveorupdate")) {
            return "Fin d’une opération de sauvegarde ou de mise à jour d’un objet métier.";
        }

        // ===== AGGREGATION / RENDERING =====
        if (lower.contains("start aggregate data")) {
            String filter = extractAfter(msg, "filter");
            String elements = extract(msg, "-\\s*(\\d+)\\s*element");

            return "Le système commence l’agrégation des données finales"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Nombre d’éléments à agréger", elements)
                    + ".";
        }

        if (lower.contains("aggregate data for")) {
            String elements = extract(msg, "Aggregate data for\\s*(\\d+)\\s*element");
            String duration = extractDuration(msg);

            return "Le système agrège les données métier finales"
                    + valuePart(". Nombre d’éléments agrégés", elements)
                    + durationPart(duration);
        }

        if (lower.contains("end aggregate data")) {
            String filter = extract(msg, "End aggregate data for\\s+(.+?)\\s+took");
            String duration = extractDuration(msg);

            return "Le système termine l’agrégation des données finales"
                    + valuePart(" pour le filtre", filter)
                    + durationPart(duration);
        }

        if (lower.contains("rendering result")) {
            String filter = extract(msg, "rendering result for\\s+(.+?)\\s+took");
            String duration = extractDuration(msg);

            return "Le système prépare le résultat final à afficher à l’utilisateur"
                    + valuePart(" pour le filtre", filter)
                    + durationPart(duration);
        }

        // ===== JBPM / WORKFLOW =====
        if (lower.contains("jbpm assignment") && lower.contains("---> process")) {
            String assignmentId = extract(msg, "Jbpm assignment\\[([^\\]]+)]");
            String process = extractBracket(msg, "Process");
            String task = extractBracket(msg, "Task");
            String assignment = extractBracket(msg, "Assignement");
            String group = extractBracket(msg, "group");
            String pathObject = extractBracket(msg, "pathObject");

            return "Le moteur JBPM prépare l’affectation d’une tâche workflow"
                    + valuePart(". Process", process)
                    + valuePart(", tâche", task)
                    + valuePart(", type d’affectation", assignment)
                    + valuePart(", groupe", group)
                    + valuePart(", pathObject", pathObject)
                    + valuePart(". Identifiant d’affectation", assignmentId)
                    + ".";
        }

        if (lower.contains("jbpm assignment") && lower.contains("assigned to")) {
            String assignmentId = extract(msg, "Jbpm assignment\\[([^\\]]+)]");
            String assignedTo = extract(msg, "assigned to\\s*>?\\s*([A-Za-z0-9_\\-]+)");

            return "Le moteur JBPM affecte la tâche workflow"
                    + valuePart(" à l’utilisateur ou groupe", assignedTo)
                    + valuePart(". Identifiant d’affectation", assignmentId)
                    + ".";
        }

        if (lower.contains("jbpmaccessor starttask")) {
            return explainPipeWorkflowAction(msg, "JBPM démarre la tâche workflow.");
        }

        if (lower.contains("jbpmaccessor endtask")) {
            return explainPipeWorkflowAction(msg, "JBPM termine la tâche workflow.");
        }

        if (lower.contains("jbpmaccessor contexttofile")) {
            return explainPipeWorkflowAction(msg, "JBPM sauvegarde le contexte de la tâche dans un fichier ou un contexte technique.");
        }

        if (lower.contains("transit task")) {
            return explainPipeWorkflowAction(msg, "JBPM effectue la transition de la tâche workflow vers l’étape suivante.");
        }

        if (lower.contains("doaction for")) {
            String uuid = extractBracketAfter(msg, "");
            String action = extract(msg, "actionName\\s*:\\s*([^,]+)");
            String transition = extract(msg, "transition\\s*:\\s*([^\\s]+)");
            String duration = extract(msg, "took\\s*(\\d+)\\s*ms");

            return "L’action métier a été exécutée"
                    + valuePart(". Action", action)
                    + valuePart(", transition", transition)
                    + valuePart(", uuid", uuid)
                    + ". Durée observée : " + safeValue(duration, "valeur inconnue") + " ms.";
        }

        // ===== STRUCTURE DATA / TOMCAT =====
        if (lower.contains("start looping over interface objects")) {
            return "Le système commence à parcourir les objets reçus depuis l’interface pour les transformer en objets métier structurés.";
        }

        if (lower.contains("new interface object")) {
            return "Un nouvel objet d’interface est détecté. Le système va commencer sa structuration champ par champ.";
        }

        if (lower.contains("calling {this.structuredata}")) {
            return "Le système appelle la structuration d’un objet interface afin de le transformer en objet métier exploitable.";
        }

        if (lower.contains("start looping over {mapinterfacefieldsbycasefields.keyset()}")) {
            return "Le système commence à parcourir les champs de l’objet interface pour les mapper vers les champs métier.";
        }

        if (lower.contains("getting {structureddata} from structuringresult")) {
            return "Le système récupère les données structurées depuis le résultat de structuration.";
        }

        if (lower.contains("getting {databkvalue} from structuringresult")) {
            return "Le système récupère la clé métier associée aux données structurées.";
        }

        if (lower.contains("getting {reldatabybkvalue} from structuringresult")) {
            return "Le système récupère les relations associées à la clé métier de l’objet.";
        }

        if (lower.contains("{structuringresult} exploided")) {
            return "Le système exploite le résultat de structuration pour récupérer les données métier, la clé métier et les relations.";
        }

        if (lower.contains("{structureddata} is not empty")) {
            return "L’objet structuré contient bien des données. Le traitement peut continuer.";
        }

        if (lower.contains("constructing {databybk}")) {
            return "Le système construit une structure dataByBK pour associer les données structurées à leur clé métier.";
        }

        if (lower.contains("{databybk} constructed")) {
            return "La structure dataByBK est construite avec succès.";
        }

        if (lower.contains("adding {structureddata} to {liststructuredinterfacedatas}")) {
            return "Le système ajoute l’objet structuré à la liste finale des données d’interface traitées.";
        }

        if (lower.contains("structuring ne field works")) {
            String worksField = extractBracketAfter(msg, "works =");
            String interfaceField = extractBracketAfter(msg, "interface =");

            return "Le système commence le mapping d’un champ d’interface vers un champ métier :"
                    + valuePart(" champ métier", worksField)
                    + valuePart(", champ interface", interfaceField)
                    + ".";
        }

        if (lower.contains("{fieldclasscode}")) {
            String fieldClassCode = extractBracketAfter(msg, "{fieldClassCode} =");

            return "Le code interne du champ métier a été identifié : "
                    + safeValue(fieldClassCode, "non détecté") + ".";
        }

        if (lower.contains("the value of the interface")) {
            String value = extractBracketAfter(msg, "=");

            return "Le système lit la valeur reçue depuis l’interface : "
                    + safeValue(value, "valeur non détectée") + ".";
        }

        if (lower.contains("parsing the value of the interface to type")) {
            String type = extractBracketAfter(msg, "type =");

            return "Le système convertit la valeur de l’interface vers le type attendu : "
                    + safeValue(type, "type non détecté") + ".";
        }

        if (lower.contains("parsing the value of the interface successful")) {
            return "La conversion de la valeur de l’interface a réussi.";
        }

        if (lower.contains("put key") && lower.contains("finalstructuredobject")) {
            String key = extractBracketAfter(msg, "put key");
            String value = extractBracketAfter(msg, "value");

            return "Le champ converti est injecté dans l’objet métier final :"
                    + valuePart(" champ", key)
                    + valuePart(", valeur", value)
                    + ".";
        }

        if (lower.contains("there is a relation to structure")) {
            return "Le champ traité implique une relation métier à construire entre objets.";
        }

        if (lower.contains("there is no relation to structure")) {
            return "Le champ traité ne nécessite pas de relation métier.";
        }

        if (lower.contains("adding the first relation")) {
            String relation = extractBracketAfter(msg, "relation");
            return "Le système initialise une première relation métier"
                    + valuePart(" nommée", relation)
                    + ".";
        }

        if (lower.contains("the pppm is null")) {
            return "Erreur métier critique : le système traite une donnée PP/PM, mais le mapping correspondant est introuvable. "
                    + "Cela signifie qu’une valeur de type personne physique/personne morale ou entité métier ne peut pas être rattachée correctement. "
                    + "Cause probable : référentiel absent, code non mappé ou configuration PP/PM incomplète.";
        }

        if (lower.contains("the error details are as follows")) {
            String column = extractBracketAfter(msg, "the column is =>");
            String attr = extractBracketAfter(msg, "the attribute =>");
            String value = extractBracketAfter(msg, "the value =>");
            String bk = extractBracketAfter(msg, "the BK =>");

            return "Détails de l’erreur métier :"
                    + valuePart(" colonne", column)
                    + valuePart(", attribut", attr)
                    + valuePart(", valeur", value)
                    + valuePart(", clé métier", bk)
                    + ". Le système a trouvé une valeur, mais n’a pas pu la rattacher au mapping PP/PM attendu.";
        }

        if (lower.contains("need to be trim")) {
            String field = extractBracket(msg, "field");
            String interfaceField = extract(msg, "interface\\s*=\\s*\\[([^\\]]+)]");

            return "Le système nettoie la valeur du champ interface"
                    + valuePart("", interfaceField)
                    + valuePart(" avant conversion vers le champ métier", field)
                    + ". Cette étape supprime les espaces ou caractères inutiles avant le mapping.";
        }

        if (lower.contains("getdatabytype") && lower.contains("start")) {
            return "Le système démarre la conversion de la valeur interface vers le type métier attendu.";
        }

        if (lower.contains("getdatabytype") && lower.contains("type [")) {
            String type = extractBracket(msg, "type");

            return "Le système identifie le type cible de conversion"
                    + valuePart("", type)
                    + ". La valeur lue depuis l’interface va être transformée vers ce type.";
        }

        if (lower.contains("getdatabytype") && lower.contains("returning")) {
            String returned = extract(msg, "returning\\s*\\[([^\\]]+)]\\s*object");

            return "La conversion de la valeur interface est terminée"
                    + valuePart(". Objet retourné", returned)
                    + ". Le système peut maintenant utiliser cette valeur dans l’objet métier structuré.";
        }

        if (lower.contains("getdatabytype") && lower.contains("return null")) {
            return "La conversion de la valeur interface a retourné null. "
                    + "Cela arrive généralement lorsque la valeur source est vide, absente ou non reconnue pour le type attendu.";
        }

        // ===== STRUCTURE DPS =====
        if (lower.contains("structure dps") && lower.contains("field is dp")) {
            return "Le système analyse un champ DPS de type DP. "
                    + "Cela signifie que le champ appartient à une donnée paramétrée ou référentielle. "
                    + "Le système vérifie donc comment l’intégrer dans l’objet final structuré.";
        }

        // ===== DATATYPE / DATABYTYPE NULL => CONTINUE =====
        if ((lower.contains("datatype is null") || lower.contains("databytype is null"))
                && lower.contains("continue")) {
            String field = extractBracketAfter(msg, "continue");
            String value = extractBracketAfter(msg, "value");

            return "Le système détecte que le type de donnée attendu est nul pour ce champ"
                    + valuePart(". Champ ignoré", field)
                    + valuePart(". Valeur reçue", value)
                    + ". Il ignore donc ce champ et continue la structuration sans l’ajouter dans l’objet final.";
        }

        if (lower.contains("structure pp/pm if field is pp/pm")) {
            return "Le système vérifie si le champ courant correspond à une donnée PP/PM. "
                    + "Cette étape permet de savoir si la valeur doit être rattachée à une personne physique, une personne morale ou une structure référentielle.";
        }

        if (lower.matches(".*\\{[^}]+}\\s+is a pp.*")) {
            String field = extract(msg, "\\{([^}]+)}\\s+is a PP");

            return "Le champ est interprété comme une donnée PP"
                    + valuePart(". Champ", field)
                    + ". Le système considère cette valeur comme liée à une personne physique ou à un acteur métier.";
        }

        if (lower.matches(".*\\{[^}]+}\\s+is a pm.*")) {
            String field = extract(msg, "\\{([^}]+)}\\s+is a PM");

            return "Le champ est interprété comme une donnée PM"
                    + valuePart(". Champ", field)
                    + ". Le système va tenter de retrouver le mapping de personne morale ou d’entité métier correspondant. "
                    + "Si ce mapping est absent, une erreur de type pppm null peut apparaître.";
        }

        if (lower.matches(".*\\{[^}]+}\\s+is a dp.*")) {
            String field = extract(msg, "\\{([^}]+)}\\s+is a DP");

            return "Le champ est interprété comme une donnée paramétrée DP"
                    + valuePart(". Champ", field)
                    + ". Le système va chercher la valeur correspondante dans un référentiel de paramètres.";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("is a simple attr")) {
            String key = extractBracket(msg, "keySet");

            return "Le système identifie un champ simple à traiter dans le référentiel de paramètres"
                    + valuePart(". Champ", key)
                    + ".";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("value is not empty")) {
            String key = extractBracket(msg, "keySet");

            return "Le système confirme que la valeur du champ paramétré est présente"
                    + valuePart(". Champ", key)
                    + ". Il peut donc continuer la recherche dans le référentiel.";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("detailparamsbypath.getbo")) {
            return "Le système trouve une liste de paramètres de référence pour le champ courant. "
                    + "Il va utiliser cette liste pour convertir ou valider la valeur métier.";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("start looping over")) {
            String count = extract(msg, "start looping over.*}\\s*(\\d+)");

            return "Le système parcourt les valeurs de référence possibles pour le champ paramétré"
                    + valuePart(". Nombre de valeurs à vérifier", count)
                    + ".";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("matching with equals pattern")) {
            return "Le système cherche une correspondance exacte dans le référentiel de paramètres avec le mode EQUALS.";
        }

        if (lower.contains("structuredetailparameter") && lower.contains("end looping over")) {
            String count = extract(msg, "end looping over.*}\\s*(\\d+)");

            return "Le système termine la recherche dans les valeurs de référence du champ paramétré"
                    + valuePart(". Nombre de valeurs vérifiées", count)
                    + ".";
        }

        if (lower.contains("relkeys.get")) {
            String relation = extract(msg, "relKeys\\.get\\(([^\\)]+)\\)");
            String value = extract(msg, "=\\s*\\[([^\\]]*)]");

            return "Le système vérifie si une clé de relation existe déjà"
                    + valuePart(". Relation", relation)
                    + valuePart(". Valeur trouvée", value)
                    + ". Si la valeur est null, une nouvelle clé de relation devra être construite.";
        }

        if (lower.contains("getting worksreldef for relation")) {
            String relation = extractBracketAfter(msg, "relation");

            return "Le système récupère la définition technique de la relation"
                    + valuePart("", relation)
                    + ". Cette définition permet de savoir comment rattacher l’objet courant à l’objet lié.";
        }

        if (lower.contains("worksreldef for relation")) {
            String ref = extractBracketAfter(msg, "relation");

            return "La définition de relation a été trouvée en mémoire"
                    + valuePart(". Référence technique", ref)
                    + ". Cette valeur est une référence Java interne, pas une donnée métier lisible.";
        }

        if (lower.contains("structured relkey")) {
            String relKey = extractBracketAfter(msg, "structured RelKey");

            return "Le système construit une nouvelle clé de relation"
                    + valuePart("", relKey)
                    + ". Cette clé sert à identifier le lien entre l’objet principal et l’objet relié.";
        }

        if (lower.contains("put new relkey inside relkeys")) {
            String relation = extractBracketAfter(msg, "rel");

            return "Le système enregistre la nouvelle clé de relation dans la liste des relations"
                    + valuePart(". Relation", relation)
                    + ". Elle pourra être utilisée pour rattacher les données liées à l’objet final.";
        }

        if (lower.contains("put reldata by reclde")) {
            String relKey = extractBracketAfter(msg, "reClde");

            return "Le système ajoute les données de relation dans le résultat structuré"
                    + valuePart(". Clé de relation", relKey)
                    + ". Cette clé permet de rattacher l’objet courant à un autre objet métier.";
        }

        if (lower.contains("structureddatawithbk")) {
            return "Le système assemble l’objet structuré final avec sa clé métier, ses champs convertis et ses relations. "
                    + "Cet objet final sera ensuite utilisé pour sauvegarder ou traiter les données métier.";
        }

        if (lower.contains("end [") && lower.contains("structuredata]")) {
            return "La structuration d’un objet d’interface est terminée. "
                    + "Les champs lus, convertis et les relations détectées sont maintenant regroupés dans un objet métier structuré.";
        }

        if (lower.contains("end call {this.structuredata}")) {
            return "Le système termine l’appel de structuration d’un objet interface. "
                    + "L’objet brut a été transformé en objet métier structuré.";
        }

        if (lower.contains("calling {this.checkmandatoryfieldsdata}")) {
            return "Le système lance la vérification des champs obligatoires de l’objet structuré.";
        }

        if (lower.contains("the mandatory fields are")) {
            String fields = extract(msg, "mandatory fields are\\s*:\\s*\\[([^\\]]+)]");
            String bk = extractBracketAfter(msg, "business key value is =>");

            return "Le système liste les champs obligatoires attendus pour valider l’objet"
                    + valuePart(". Champs obligatoires", fields)
                    + valuePart(". Clé métier", bk)
                    + ". Si la clé métier est nulle, l’objet peut être difficile à identifier correctement.";
        }

        if (lower.contains("{mandatorymapping}")) {
            String field = extractBracketAfter(msg, "{mandatoryMapping}");
            String bk = extractBracketAfter(msg, "business key value is =>");

            return "Le système vérifie un champ obligatoire précis"
                    + valuePart(". Champ obligatoire", field)
                    + valuePart(". Clé métier", bk)
                    + ".";
        }

        if (lower.contains("the interface respect the mandatory fields")) {
            return "La vérification des champs obligatoires est réussie. "
                    + "L’objet interface contient les données minimales attendues pour continuer le traitement.";
        }

        if (lower.contains("business key value is => [null]")) {
            return "La clé métier de l’objet est nulle. Cela peut empêcher l’identification correcte de l’objet traité.";
        }

        // ===== TECHNICAL / TRIGGERS =====
        if (lower.contains("triggercachecleaner") && lower.contains("complete")) {
            return "Le nettoyage automatique du cache s’est terminé. Ce log est technique et ne correspond pas directement à une action utilisateur.";
        }

        if (lower.contains("trigger") && lower.contains("was fired")) {
            String trigger = extract(msg, "^(.*?)\\s+was fired");
            return "Un déclencheur automatique système a été lancé"
                    + valuePart(". Trigger", trigger)
                    + ". Ce log correspond à une tâche planifiée, pas directement à une action utilisateur.";
        }

        if (lower.contains("trigger") && lower.contains("is complete")) {
            String trigger = extract(msg, "^(.*?)\\s+is complete");
            return "Un déclencheur automatique système s’est terminé"
                    + valuePart(". Trigger", trigger)
                    + ".";
        }

        // ===== RELATIONS SAVE DETAILS =====
        if (lower.contains("insertarrayrel")) {
            String table = extract(msg, "insertArrayRel\\s+([^\\s]+)");
            String uuid = extractUuid(msg);
            String elements = extract(msg, "for\\s*\\[(\\d+)]\\s*element");
            String duration = extractDuration(msg);

            return "Le système insère en base des relations entre objets métier"
                    + valuePart(" dans la table", table)
                    + valuePart(". Nombre d’éléments", elements)
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        // ===== PRINT / CUPS =====
        if (lower.contains("status after") && lower.contains("completed")) {
            String seconds = extract(msg, "status after\\s*(\\d+)\\s*sec");
            String status = extract(msg, "=\\s*([^\\s\\|]+)");

            return "Le système vérifie le statut du job d’impression"
                    + valuePart(". Délai de vérification", seconds, " seconde(s)")
                    + valuePart(". Statut", status)
                    + ".";
        }

        if (lower.contains("get last printjob")) {
            return "Le système récupère le dernier job d’impression afin de vérifier son état.";
        }

        if (lower.matches(".*id:\\s*\\d+.*user:.*url:.*status:.*")) {
            String id = extract(msg, "ID:\\s*(\\d+)");
            String user = extract(msg, "user:\\s*([^\\s]+)");
            String url = extract(msg, "url:\\s*([^\\s]+)");
            String status = extract(msg, "status:\\s*([^\\s\\|]+)");

            return "Le système a récupéré les informations du dernier job d’impression"
                    + valuePart(". Job ID", id)
                    + valuePart(". Utilisateur", user)
                    + valuePart(". URL", url)
                    + valuePart(". Statut", status)
                    + ".";
        }

        if (lower.contains("printing result")) {
            String document = extractBracket(msg, "Document");
            String result = extractBracket(msg, "Printing Result");

            return "Le système indique le résultat d’impression du document"
                    + valuePart("", document)
                    + valuePart(". Résultat", result)
                    + ". Si le résultat vaut INF_PRINT_OK, l’impression est considérée comme réussie.";
        }

        // ===== PROCESS / WORKFLOW BUSINESS LOGS =====

        // ===== PROCESS / DUM DOUANE =====
        if (lower.contains("start ws_insert_dum_douane")) {
            return "Le système démarre l’insertion des données douanières DUM. "
                    + "Cette étape crée ou met à jour la déclaration douanière avec ses articles, moyens de transport et informations associées.";
        }

        if (lower.contains("end ws_insert_dum_douane")) {
            String duration = extractDuration(msg);
            return "Le système termine l’insertion des données douanières DUM."
                    + durationPart(duration);
        }

        if (lower.contains("new dum :: creat")) {
            String dum = extractAfterDashesOrTail(msg);

            return "Le système crée une nouvelle DUM"
                    + valuePart(". Référence DUM", dum)
                    + ". Cela signifie qu’aucune déclaration existante correspondante n’a été retrouvée et qu’un nouvel objet douanier est créé.";
        }

        if (lower.contains("cusdec xml")) {
            return "Le système traite le flux XML douanier CusDec. "
                    + "Ce message indique que les données de déclaration douanière sont présentes et vont être exploitées.";
        }

        if (lower.contains("start ws_insert_dum")) {
            return "Le système démarre l’insertion principale de la DUM. "
                    + "Cette étape prépare l’enregistrement de la déclaration douanière dans le système.";
        }

        if (lower.contains("start ws_insert_importateur_dum")) {
            return "Le système démarre l’insertion ou la vérification de l’importateur lié à la DUM.";
        }

        if (lower.contains("start ws_insert_exportateur_dum")) {
            return "Le système démarre l’insertion ou la vérification de l’exportateur lié à la DUM.";
        }

        if (lower.contains("iceexportateur")) {
            String ice = extractBracketAfter(msg, "iceExportateur");

            return "Le système lit l’ICE de l’exportateur"
                    + valuePart("", ice)
                    + ". Cet identifiant sert à retrouver ou créer le partenaire commercial lié à la DUM.";
        }

        if (lower.contains("start find bpartner with ice")) {
            String ice = extractBracketAfter(msg, "ice");

            return "Le système recherche un partenaire commercial avec l’ICE"
                    + valuePart("", ice)
                    + ". Cette étape vérifie si l’importateur/exportateur existe déjà dans le référentiel.";
        }

        if (lower.contains("end find bpartner with ice")) {
            String ice = extractBracketAfter(msg, "ice");

            return "La recherche du partenaire commercial par ICE est terminée"
                    + valuePart(". ICE", ice)
                    + ".";
        }

        if (lower.contains("ice n'existe pas") || lower.contains("ice nexiste pas")) {
            String ice = extractAfterDashesOrTail(msg);

            return "Le système n’a trouvé aucun partenaire commercial pour l’ICE"
                    + valuePart("", ice)
                    + ". Cela indique probablement que l’importateur/exportateur doit être créé ou que le référentiel partenaire n’est pas encore alimenté.";
        }

        // ===== DUM ARTICLE / CONTENEUR / MOYEN TRANSPORT =====
        if (lower.contains("ws_dum_moyentransport_v2") && lower.contains("start")) {
            return "Le système démarre le traitement des moyens de transport associés à la DUM.";
        }

        if (lower.contains("ws_dum_moyentransport_v2") && lower.contains("json mt vide")) {
            return "Le système vérifie les moyens de transport de la DUM, mais le JSON reçu est vide. "
                    + "Aucune insertion ni suppression de moyen de transport n’est nécessaire.";
        }

        if (lower.contains("ws_dum_moyentransport_v2") && lower.contains("end")) {
            String duration = extractProcessDurationEquals(msg);
            return "Le système termine le traitement des moyens de transport de la DUM."
                    + durationPart(duration);
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("start")) {
            return "Le système démarre le contrôle des articles et conteneurs liés à la DUM.";
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("json idconteneur count")) {
            String count = extract(msg, "count=(\\d+)");

            return "Le système lit le nombre de conteneurs transmis dans le JSON DUM"
                    + valuePart(". Nombre de conteneurs", count)
                    + ".";
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("existfromfilter size")) {
            String noDum = extract(msg, "noDUM=([^\\s\\|]+)");
            String size = extract(msg, "existFromFilter size=(\\d+)");

            return "Le système vérifie les articles/conteneurs déjà existants pour la DUM"
                    + valuePart("", noDum)
                    + valuePart(". Nombre trouvé via filtre", size)
                    + ".";
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("existing=")) {
            String noDum = extract(msg, "noDUM=([^\\s\\|]+)");
            String existing = extract(msg, "existing=(\\d+)");
            String protectedCount = extract(msg, "protected\\(consumed=true\\)=(\\d+)");

            return "Le système compare les articles/conteneurs existants avec les données reçues pour la DUM"
                    + valuePart("", noDum)
                    + valuePart(". Existants", existing)
                    + valuePart(". Protégés car déjà consommés", protectedCount)
                    + ".";
        }

        // ===== DUM / MAINLEVEE COUNTERS =====
        if (lower.contains("numeroarticle")) {
            String numero = extractBracketAfter(msg, "NumeroArticle");

            return "Le système traite un article de déclaration douanière"
                    + valuePart(". Numéro article", numero)
                    + ".";
        }

        if (lower.contains("listlignesapurement size")) {
            String size = extract(msg, "listLignesApurement size\\s*(\\d+)");

            return "Le système lit les lignes d’apurement associées à la MainLevée"
                    + valuePart(". Nombre de lignes", size)
                    + ". Ces lignes permettent de rattacher la MainLevée aux déclarations ou articles concernés.";
        }

        // ===== BAD / CONTROLE BAD =====
        if (lower.contains("start ctrl_insertbad")) {
            return "Le système démarre le contrôle avant insertion d’un BAD. "
                    + "Cette étape vérifie les équipements, marchandises et données associées avant d’enregistrer le BAD.";
        }

        if (lower.contains("ctrl bad")) {
            String bad = extractAfterDashesOrTail(msg);

            return "Le système contrôle le BAD"
                    + valuePart(". Référence BAD", bad)
                    + ".";
        }

        if (lower.contains("ctrl_insertbad start filtre badequipements")) {
            String ref = extractAfterDashesOrTail(msg);

            return "Le système démarre le filtre de contrôle des équipements BAD"
                    + valuePart(". Référence BAD", ref)
                    + ". Cette étape vérifie les équipements associés au BAD avant insertion.";
        }

        if (lower.contains("ctrl_insertbad end filtre badequipements")) {
            String duration = extractDuration(msg);

            return "Le système termine le contrôle des équipements BAD."
                    + durationPart(duration);
        }

        if (lower.contains("end ctrl_insertbad")) {
            String duration = extractDuration(msg);

            return "Le contrôle avant insertion du BAD est terminé."
                    + durationPart(duration);
        }

        if (lower.contains("start ws_insertbad")) {
            return "Le système démarre l’insertion du BAD. "
                    + "Cette opération enregistre un document ou dossier BAD avec ses marchandises et équipements associés.";
        }

        if (lower.matches(".*\\bbad\\b.*\\d{6,}.*")) {
            String bad = extractAfterDashesOrTail(msg);

            return "Le système traite le BAD"
                    + valuePart(". Référence BAD", bad)
                    + ".";
        }

        if (lower.contains("ws_insertbad start filtre bad")) {
            return "Le système démarre le filtre principal d’insertion BAD. "
                    + "Cette étape recherche ou prépare l’objet BAD avant sauvegarde.";
        }

        if (lower.contains("ws_insertbad end filtre bad")) {
            String duration = extractDuration(msg);

            return "Le système termine le filtre principal d’insertion BAD."
                    + durationPart(duration);
        }

        if (lower.contains("ws_save_bad_y") && lower.contains("etat was null")) {
            return "Le système constate que l’état du BAD est absent. "
                    + "Il devra probablement appliquer une valeur par défaut ou continuer avec un état non renseigné.";
        }

        if (lower.contains("ws_save_bad_y") && lower.contains("badconsomme")) {
            String value = extract(msg, "badConsomme\\s*=\\s*([^\\s\\|]+)");

            return "Le système vérifie si le BAD est déjà consommé"
                    + valuePart(". badConsomme", value)
                    + ". Si la valeur est false, le BAD reste disponible ou non consommé.";
        }

        if (lower.contains("ws_save_bad_y") && lower.contains("badjson.owner.codefret")) {
            String value = extract(msg, "badJson\\.owner\\.codeFret=\\s*([^\\s\\|]+)");

            return "Le système lit le code fret du propriétaire dans le JSON BAD"
                    + valuePart(". codeFret JSON", value)
                    + ". Une valeur null indique que cette information n’a pas été transmise.";
        }

        if (lower.contains("ws_save_bad_y") && lower.contains("bad.ownercodefret")) {
            String value = extract(msg, "bad\\.ownerCodeFret=\\s*([^\\s\\|]+)");

            return "Le système lit le code fret du propriétaire dans l’objet BAD"
                    + valuePart(". ownerCodeFret", value)
                    + ". Une valeur null indique que le rattachement propriétaire/fret est absent.";
        }

        if (lower.contains("dateexpiration string")) {
            String date = extractAfterDashesOrTail(msg);

            return "Le système lit la date d’expiration du BAD"
                    + valuePart("", date)
                    + ". Cette date indique jusqu’à quand le BAD reste valide.";
        }

        if (lower.contains("listmarchtodelete deleted")) {
            String count = extractAfterDashesOrTail(msg);

            return "Le système supprime les marchandises BAD marquées pour suppression"
                    + valuePart(". Nombre supprimé", count)
                    + ".";
        }

        if (lower.contains("listequiptodelete deleted")) {
            String count = extractAfterDashesOrTail(msg);

            return "Le système supprime les équipements BAD marqués pour suppression"
                    + valuePart(". Nombre supprimé", count)
                    + ".";
        }

        if (lower.contains("listlmbad size")) {
            String size = extract(msg, "listLmBad size\\s*(\\d+)");

            return "Le système lit les lignes de marchandise associées au BAD"
                    + valuePart(". Nombre de lignes", size)
                    + ".";
        }

        if (lower.contains("listseq size")) {
            String size = extract(msg, "listsEq size\\s*(\\d+)");

            return "Le système lit les équipements associés au BAD"
                    + valuePart(". Nombre d’équipements", size)
                    + ".";
        }

        // ===== TRACABILITE ZONE FRANCHE / SERVICES IGNORED =====
        if (lower.contains("start ws_inserttracabilitezonefranche")) {
            return "Le système démarre l’insertion d’une traçabilité Zone Franche. "
                    + "Cette opération enregistre le passage ou l’état d’une unité dans le contexte Zone Franche.";
        }

        if (lower.contains("end ws_inserttracabilitezonefranche")) {
            String duration = extractDuration(msg);

            return "Le système termine l’insertion de la traçabilité Zone Franche."
                    + durationPart(duration);
        }

        if (lower.contains("start ws_updatetracabilitezonefranche")) {
            return "Le système démarre la mise à jour de la traçabilité Zone Franche. "
                    + "Cette opération met à jour le statut, le dernier checkpoint ou les informations de suivi de l’unité.";
        }

        if (lower.contains("end ws_updatetracabilitezonefranche")) {
            String duration = extractDuration(msg);

            return "Le système termine la mise à jour de la traçabilité Zone Franche."
                    + durationPart(duration);
        }

        if (lower.contains("start ws_updateservicesortie")) {
            return "Le système démarre la mise à jour du service de sortie. "
                    + "Cette étape sert à finaliser ou synchroniser la sortie de l’unité.";
        }

        if (lower.contains("start ws_insertembarquement")) {
            return "Le système démarre l’insertion de l’embarquement. "
                    + "Cette étape prépare ou enregistre l’embarquement de l’unité.";
        }

        if (lower.contains("start ws_updateunitefret")) {
            return "Le système démarre la mise à jour de l’unité de fret. "
                    + "Cette étape met à jour les informations logistiques de l’unité suivie.";
        }

        if (lower.contains("start ws_insert_vusortieport")) {
            return "Le système démarre l’insertion de la vue de sortie port. "
                    + "Cette opération enregistre une trace de sortie du port.";
        }

        if (lower.contains("start ws_update_equipement_bad")) {
            return "Le système démarre la mise à jour des équipements BAD.";
        }

        if (lower.contains("start ws_insert_maq")) {
            return "Le système démarre l’insertion MAQ, probablement liée à une mise à quai ou une opération portuaire de positionnement.";
        }

        if (lower.contains(" ignored")) {
            String service = extractIgnoredServiceName(msg);

            return "Le système ignore volontairement l’exécution du service"
                    + valuePart("", service)
                    + ". Cela indique qu’une règle de contrôle, un flag IgnoreAllRules ou une condition métier empêche cette opération d’être exécutée.";
        }

        // ===== TRACABILITE SAS OBJECT =====
        if (lower.contains("tracabilitesas ::")) {
            String noUnite = extractMapField(msg, "noUnite");
            String status = extractMapField(msg, "status");
            String source = extractMapField(msg, "source");
            String amp = extractMapField(msg, "amp");
            String noService = extractMapField(msg, "noService");
            String nature = extractMapField(msg, "natureMarchandise");
            String dateAction = extractMapField(msg, "dateAction");
            String delai = extractMapField(msg, "delai");

            return "Le système affiche l’objet complet de traçabilité SAS"
                    + valuePart(". Unité", noUnite)
                    + valuePart(". Service", noService)
                    + valuePart(". AMP", amp)
                    + valuePart(". Source", source)
                    + valuePart(". Statut", status)
                    + valuePart(". Nature marchandise", nature)
                    + valuePart(". Date action", dateAction)
                    + valuePart(". Délai", delai)
                    + ". Cette ligne résume l’état métier complet de l’unité dans le suivi de traçabilité.";
        }

        if (lower.matches(".*\\bdelai\\s*\\[[^\\]]+].*")) {
            String delai = extractBracketAfter(msg, "delai");

            return "Le système calcule ou lit le délai associé au traitement"
                    + valuePart("", delai)
                    + ". Ce délai représente une durée métier entre deux étapes ou entre la création et la validation.";
        }

        // ===== PROCESS CONTEXT LIST =====
        if (lower.startsWith("[") && lower.contains("processlogger") && lower.contains("uuid")) {
            return "Le système affiche les clés de contexte disponibles pour le process. "
                    + "Ces informations indiquent les paramètres présents en mémoire pour exécuter le workflow, comme uuid, processName, taskName, ACTION_NAME ou les objets métier transmis.";
        }

        // BAD_REG
        if (lower.contains("bad_reg start filtre")) {
            String refBad = extractProcessValue(msg, "refBad");

            return "Le système démarre le filtre BAD_REG pour vérifier un enregistrement potentiellement non conforme"
                    + valuePart(". Référence BAD", refBad)
                    + ". Ce filtre sert à retrouver ou contrôler une donnée signalée comme anomalie ou cas particulier.";
        }

        if (lower.contains("bad_reg end filtre")) {
            String count = extract(msg, "::\\s*(\\d+)");

            return "Le système termine le filtre BAD_REG"
                    + valuePart(". Nombre d’éléments détectés", count)
                    + ". Un résultat supérieur à zéro signifie qu’un enregistrement BAD correspondant a été trouvé.";
        }

        // Start/End filtre process
        if (lower.contains("start filtre")) {
            String filter = extractProcessFilterName(msg);
            String amp = extractProcessValue(msg, "amp");
            String ampe = extractProcessValue(msg, "ampe");
            String refBad = extractProcessValue(msg, "refBad");

            return "Le système démarre l’exécution du filtre métier"
                    + valuePart("", filter)
                    + valuePart(". Identifiant AMP", firstNonBlank(amp, ampe))
                    + valuePart(". Référence BAD", refBad)
                    + ". Ce filtre est une étape intermédiaire du pipeline de recherche ou de contrôle.";
        }

        if (lower.contains("end filtre")) {
            String filter = extractProcessFilterName(msg);
            String size = extract(msg, "::\\s*(\\d+)");
            String serviceSize = extract(msg, "SIZE\\s*::\\s*(\\d+)");

            return "Le système termine l’exécution du filtre métier"
                    + valuePart("", filter)
                    + valuePart(". Nombre d’éléments retenus", firstNonBlank(size, serviceSize))
                    + ". Ce résultat indique combien d’éléments restent après cette étape de filtrage.";
        }
        // currentCheckPoint
        if (lower.contains("currentcheckpoint")) {
            String cp = extractStarValue(msg, "currentCheckPoint");
            if (cp == null) {
                cp = extractProcessValue(msg, "currentCheckPoint");
            }

            return "Le système positionne le traitement sur le point métier courant"
                    + valuePart("", cp)
                    + ". Ce checkpoint décrit l’étape réelle où se trouve l’objet dans le workflow.";
        }

        // Find service


        // WS_RFID_FindServices
        if (lower.contains("start ws_rfid_findservices")) {
            return "Le système démarre une recherche de services basée sur les informations RFID. "
                    + "Cette étape sert à retrouver le service ou l’unité correspondant au tag RFID, à la position ou au numéro d’unité.";
        }

        if (lower.contains("end ws_rfid_findservices")) {
            return "La recherche RFID des services est terminée. "
                    + "Le système a fini de vérifier les correspondances entre RFID, unité, position et service.";
        }

        if (lower.contains("start statutscan")) {
            return "Le système démarre la vérification du statut de scan RFID.";
        }

        if (lower.contains("end statutscan")) {
            return "Le système termine la vérification du statut de scan RFID.";
        }

        if (lower.contains("start reg")) {
            return "Le système démarre la recherche REG liée au traitement RFID.";
        }

        if (lower.contains("end reg")) {
            return "Le système termine la recherche REG liée au traitement RFID.";
        }

        // RFID values
        if (lower.contains("norfid")) {
            String noRfid = extractStarValue(msg, "noRFID");

            return "Le système traite le tag RFID"
                    + valuePart("", noRfid)
                    + ". Ce tag permet d’identifier automatiquement l’unité ou l’objet suivi.";
        }

        if (lower.contains("tag rfid")) {
            String tag = extractStarValue(msg, "TAG RFID");

            return "Le système lit un tag RFID"
                    + valuePart("", tag)
                    + ". Cette valeur provient d’une lecture RFID physique.";
        }

        if (lower.contains("notag")) {
            String tag = extractProcessValue(msg, "noTAG");

            return "Le système identifie le tag RFID associé"
                    + valuePart("", tag)
                    + ". Ce tag est utilisé pour retrouver ou confirmer l’unité concernée.";
        }

        if (lower.contains("noservice")) {
            String service = extractStarValue(msg, "noService");
            if (service == null) {
                service = extractProcessValue(msg, "noService");
            }

            return "Le système traite le service"
                    + valuePart("", service)
                    + ". Ce numéro sert à rattacher l’action ou la traçabilité au service portuaire concerné.";
        }

        if (lower.contains("nounite")) {
            String unite = extractStarValue(msg, "noUnite");
            if (unite == null) {
                unite = extractProcessValue(msg, "noUnite");
            }

            return "Le système identifie l’unité concernée"
                    + valuePart("", unite)
                    + ". Cette unité peut correspondre à un conteneur, véhicule, remorque ou autre objet suivi.";
        }

        if (lower.matches(".*\\*+\\s*version\\s*\\*+.*")) {
            String version = extractStarValue(msg, "version");

            return "Le système lit la version du message RFID"
                    + valuePart("", version)
                    + ". Cette valeur permet d’interpréter le format ou la variante du message reçu.";
        }

        if (lower.contains("datecheckpoint")) {
            String date = extractStarValue(msg, "dateCheckPoint");

            return "Le système lit la date du checkpoint"
                    + valuePart("", date)
                    + ". Cette date correspond au moment du passage détecté.";
        }

        if (lower.contains("etatunite")) {
            String etat = extractStarValue(msg, "etatUnite");

            return "Le système lit l’état ou l’horodatage associé à l’unité"
                    + valuePart("", etat)
                    + ". Cette information aide à comprendre l’état de l’unité au moment du checkpoint.";
        }

        // Generic RFID V2 parameters
        if (lower.contains("numeroamp")) {
            String value = extractStarValue(msg, "numeroAMP");

            if (value == null || value.isBlank()) {
                return "Le système vérifie le numéro AMP reçu par le service RFID, mais aucune valeur exploitable n’est présente dans cette ligne.";
            }

            return "Le système lit le numéro AMP"
                    + valuePart("", value)
                    + ". Cette valeur sert à relier la lecture RFID à un service ou dossier métier.";
        }

        if (lower.contains("numerotag ou numerounite null")) {
            return "Le système détecte que le numéro de tag ou le numéro d’unité est absent. "
                    + "Cela peut empêcher l’identification complète de l’objet suivi par RFID.";
        }

        if (lower.contains("numerotag")) {
            String value = extractStarValue(msg, "numeroTag");

            if (value == null || value.isBlank()) {
                return "Le système vérifie le numéro de tag RFID, mais la valeur est absente dans cette ligne.";
            }

            return "Le système lit le numéro de tag RFID"
                    + valuePart("", value)
                    + ".";
        }

        if (lower.contains("position")) {
            String position = extractStarValue(msg, "position");

            return "Le système lit la position RFID déclarée"
                    + valuePart("", position)
                    + ". Cette position correspond au point physique ou logique où l’unité est détectée.";
        }

        if (lower.contains("numerounite")) {
            String value = extractStarValue(msg, "numeroUnite");

            return "Le système lit le numéro d’unité transmis au service RFID"
                    + valuePart("", value)
                    + ".";
        }

        // WS_INSERT_TRACABILITE_RFID / TRACABILITE
        if (lower.contains("start ws_insert_tracabilite_rfid")) {
            return "Le système démarre l’insertion d’un événement de traçabilité RFID. "
                    + "Cette opération va enregistrer le passage détecté avec le tag RFID, l’unité, le service et le checkpoint.";
        }

        if (lower.contains("end ws_insert_tracabilite_rfid")) {
            String duration = extractDuration(msg);

            return "Le système termine l’insertion de l’événement de traçabilité RFID."
                    + durationPart(duration);
        }

        if (lower.contains("start ws_inserttracabilite")) {
            return "Le système démarre l’insertion d’un événement de traçabilité métier. "
                    + "Cette opération enregistre un passage ou une action dans l’historique de suivi.";
        }

        // MAJ_Insert / MAJ_Update
        if (lower.matches(".*start\\s+maj_(insert|update).*")) {
            String name = extract(msg, "START\\s+(MAJ_(?:Insert|Update)[^\\s\\-]+)");

            return "Le système démarre une opération de mise à jour métier"
                    + valuePart("", name)
                    + ". Cette étape modifie ou crée des données liées au workflow.";
        }

        if (lower.matches(".*end\\s+maj_(insert|update).*")) {
            String name = extract(msg, "END\\s+(MAJ_(?:Insert|Update)[^\\s\\-]+)");
            String duration = extractDuration(msg);

            return "Le système termine l’opération de mise à jour métier"
                    + valuePart("", name)
                    + durationPart(duration);
        }

        // WS_Insert_AMP_DOC
        if (lower.contains("ws_insert_amp_doc") && lower.contains("start doc amp")) {
            return "Le système démarre le traitement des documents AMP. "
                    + "Il va vérifier les documents à sauvegarder, les documents obligatoires et mettre à jour la liste documentaire liée au service.";
        }

        if (lower.contains("listdoctosave")) {
            String list = extractProcessValue(msg, "listDocToSave");

            return "Le système lit la liste des documents à sauvegarder"
                    + valuePart("", list)
                    + ". Cette liste contient les documents transmis pour rattachement à l’AMP.";
        }

        if (lower.contains("amp found")) {
            return "Le système a retrouvé l’AMP concerné. "
                    + "Le traitement documentaire peut donc continuer sur le bon objet métier.";
        }

        if (lower.contains("listdocoblig")) {
            String list = extractProcessValue(msg, "listDocOblig");

            return "Le système vérifie les documents obligatoires attendus"
                    + valuePart("", list)
                    + ". Si la liste est vide, aucun document obligatoire supplémentaire n’est attendu dans cette étape.";
        }

        if (lower.contains("typedoctosave")) {
            String type = extractProcessValue(msg, "typeDocToSave");

            return "Le système identifie le type de document à sauvegarder"
                    + valuePart("", type)
                    + ". Ce type permet de classer le document dans le dossier AMP.";
        }

        if (lower.contains("doc amp start updating")) {
            String before = extract(msg, "Before\\s+servicePortDoc\\.documents=(\\d+)");
            String after = extract(msg, "After\\s+servicePortDoc\\.documents=(\\d+)");
            String deleteCount = extract(msg, "boWSDocToDelete=(\\d+)");

            return "Le système démarre la mise à jour des documents AMP"
                    + valuePart(". Documents avant modification", before)
                    + valuePart(". Documents après modification", after)
                    + valuePart(". Documents à supprimer", deleteCount)
                    + ".";
        }

        if (lower.contains("todetele") || lower.contains("todelete")) {
            String type = extract(msg, "(TYPE_DOC_[A-Z0-9_]+)");
            String toDelete = extract(msg, "toDelete\\[?([^\\s\\|\\]]+)");

            return "Le système vérifie si le document doit être supprimé"
                    + valuePart(". Type document", type)
                    + valuePart(". Suppression demandée", toDelete)
                    + ".";
        }

        if (lower.contains("doc amp updated")) {
            return "Les documents AMP ont été mis à jour avec succès.";
        }

        if (lower.contains("end doc amp")) {
            return "Le traitement des documents AMP est terminé.";
        }

        // MainLevée
        if (lower.contains("start ws_insert_mainlevee")) {
            return "Le système démarre l’insertion d’une MainLevée. "
                    + "Il va vérifier si une MainLevée existe déjà, puis créer ou enrichir les données associées.";
        }

        if (lower.contains("listmainlevee size")) {
            String size = extract(msg, "listMainLevee size\\s*:\\s*\\[(\\d+)]");

            return "Le système vérifie les MainLevées existantes"
                    + valuePart(". Nombre trouvé", size)
                    + ". Si la valeur est 0, une nouvelle MainLevée devra être créée.";
        }

        if (lower.contains("new mainlevee")) {
            String ref = extract(msg, "New MainLevee\\s*::\\s*Creat\\s*-+\\s*([^\\s\\|]+)");

            return "Le système crée une nouvelle MainLevée"
                    + valuePart(". Référence", ref)
                    + ". Cette création indique qu’aucune MainLevée existante n’a été retrouvée pour ce contexte.";
        }

        if (lower.contains("avecscanner")) {
            String value = extract(msg, "AvecScanner\\s+newML\\s*-+\\s*=\\s*([^\\|]+)");

            return "Le système lit l’information scanner de la MainLevée"
                    + valuePart("", value)
                    + ". Cette valeur indique si la MainLevée est liée à un passage scanner.";
        }

        if (lower.contains("avecpesage")) {
            String value = extract(msg, "AvecPesage\\s+newML\\s*-+\\s*=\\s*([^\\|]+)");

            return "Le système lit l’information de pesage de la MainLevée"
                    + valuePart("", value)
                    + ".";
        }

        if (lower.contains("dateml")) {
            String date = extract(msg, "DateML\\s*:\\s*([^\\|]+)");

            return "Le système lit la date de MainLevée"
                    + valuePart("", date)
                    + ".";
        }

        if (lower.contains("start lignesapurement")) {
            return "Le système démarre le traitement des lignes d’apurement liées à la MainLevée.";
        }

        if (lower.contains("end lignesapurement")) {
            return "Le système termine le traitement des lignes d’apurement liées à la MainLevée.";
        }

        if (lower.contains("start articles")) {
            return "Le système démarre le traitement des articles associés à la MainLevée.";
        }

        if (lower.contains("listarticles size")) {
            String size = extract(msg, "listArticles size\\s*(\\d+)");

            return "Le système lit le nombre d’articles associés à la MainLevée"
                    + valuePart("", size)
                    + ".";
        }

        if (lower.contains("end articles")) {
            return "Le système termine le traitement des articles associés à la MainLevée.";
        }

        // DUM Article / Conteneur
        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("insert none")) {
            String noDum = extract(msg, "noDUM=([^\\s\\|]+)");

            return "Le système vérifie les articles/conteneurs de la DUM"
                    + valuePart("", noDum)
                    + ", mais aucune insertion n’est nécessaire.";
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("delete none")) {
            String noDum = extract(msg, "noDUM=([^\\s\\|]+)");

            return "Le système vérifie les suppressions d’articles/conteneurs pour la DUM"
                    + valuePart("", noDum)
                    + ", mais aucune suppression n’est nécessaire.";
        }

        if (lower.contains("ws_dum_articleconteneur_v2") && lower.contains("inserted=")) {
            String inserted = extract(msg, "inserted=(\\d+)");
            String deleted = extract(msg, "deleted=(\\d+)");
            String skipped = extract(msg, "skippedProtectedDelete=(\\d+)");
            String duration = extract(msg, "took\\(ms\\)=(\\d+)");

            return "Le traitement des articles/conteneurs DUM est terminé"
                    + valuePart(". Insertions", inserted)
                    + valuePart(". Suppressions", deleted)
                    + valuePart(". Suppressions protégées ignorées", skipped)
                    + durationPart(duration);
        }

        // Simple business values specific to process
        if (lower.contains("tracteur")) {
            String value = extractProcessValue(msg, "tracteur");

            return "Le système lit le tracteur concerné par l’opération"
                    + valuePart("", value)
                    + ". Cette valeur identifie le véhicule lié au mouvement ou au passage.";
        }

        if (lower.contains("nodum")) {
            String value = extract(msg, "noDUM\\s*[:=]\\s*([^\\s\\|]+)");

            return "Le système traite la DUM"
                    + valuePart("", value)
                    + ". Cette référence identifie une déclaration douanière.";
        }

        if (lower.contains("selectivitedecontrol")) {
            String value = extract(msg, "selectiviteDeControl\\s*:\\s*([^\\|]*)");

            return "Le système lit la sélectivité de contrôle"
                    + valuePart("", value)
                    + ". Cette valeur indique le niveau ou type de contrôle douanier appliqué.";
        }

        // ===== CUSTOM BUSINESS RULES / MAJ INSERT =====
        if (lower.contains("insertion reussie") || lower.contains("insertion réussie")) {
            String result = extract(msg, "Insertion\\s+reussie\\s*::\\s*([^\\s\\|]+)");
            if (result == null) {
                result = extract(msg, "Insertion\\s+réussie\\s*::\\s*([^\\s\\|]+)");
            }

            return "Le système indique le résultat de l’insertion métier"
                    + valuePart(". Succès", result)
                    + ". Si la valeur est true, l’insertion a réussi.";
        }

        // ===== PROCESS / DOWNLOAD INVOICE =====
        if (lower.contains("ws_download_invoice")) {
            if (lower.contains("codedeclarant")) {
                String code = extractAfterDashesOrTail(msg);
                return "Le système lit le code déclarant pour le téléchargement de facture"
                        + valuePart(". codeDeclarant", code)
                        + ". Si la valeur est null, aucun déclarant n’est rattaché à cette facture.";
            }

            if (lower.contains("nobill=") || lower.contains("relatedinvoice") || lower.contains("tottc=")) {
                String noBill = extractMapField(msg, "noBill");
                String totTtc = extractMapField(msg, "totTTC");
                String activity = extractMapField(msg, "activity");
                String doneBy = extractMapField(msg, "doneBy");
                String flgOracle = extractMapField(msg, "flgOracle");

                return "Le système affiche les données principales de la facture à télécharger"
                        + valuePart(". Facture", noBill)
                        + valuePart(". Activité", activity)
                        + valuePart(". Montant TTC", totTtc)
                        + valuePart(". Créée par", doneBy)
                        + valuePart(". Statut Oracle", flgOracle)
                        + ". Cette ligne résume le contenu métier de la facture.";
            }
        }

        // ===== PROCESS / SAVE EMH =====
        if (lower.contains("ws_save_emh") && lower.contains("start filtre")) {
            String filter = extractProcessFilterName(msg);
            String ref = extractAfterDashesOrTail(msg);

            return "Le système démarre un filtre du service WS_SAVE_EMH"
                    + valuePart(". Filtre", filter)
                    + valuePart(". Référence", ref)
                    + ". Cette étape prépare ou vérifie les données EMH avant sauvegarde.";
        }

        if (lower.contains("ws_save_emh") && lower.contains("end filtre")) {
            String filter = extractProcessFilterName(msg);
            String ref = extractAfterDashesOrTail(msg);

            return "Le système termine un filtre du service WS_SAVE_EMH"
                    + valuePart(". Filtre", filter)
                    + valuePart(". Référence", ref)
                    + ".";
        }

        if (lower.contains("update emh start")) {
            String ref = extractAfterDashesOrTail(msg);

            return "Le système démarre la mise à jour EMH"
                    + valuePart(". Référence", ref)
                    + ". Cette étape modifie les informations EMH existantes.";
        }

        if (lower.contains("emh actif")) {
            String ref = extractAfterDashesOrTail(msg);

            return "Le système confirme que l’EMH est actif"
                    + valuePart(". Référence", ref)
                    + ". L’objet peut donc être utilisé ou sauvegardé.";
        }

        if (lower.contains("end ws_save_emh")) {
            String duration = extractDuration(msg);
            String ref = extractAfterDashesOrTail(msg);

            return "Le système termine le traitement WS_SAVE_EMH"
                    + valuePart(". Référence", ref)
                    + ". L’opération de création ou mise à jour EMH est finalisée."
                    + durationPart(duration);
        }

        if (lower.matches(".*[a-zA-Z0-9_]+\\s*::\\s*.*")) {
            String key = extract(msg, "([A-Za-z0-9_]+)\\s*::");
            String value = extract(msg, "::\\s*([^\\|]+)");

            return "Valeur métier détectée dans le traitement"
                    + valuePart(". Champ", key)
                    + valuePart(". Valeur", value)
                    + ". Cette information peut servir à comprendre le contexte de la règle ou de l’insertion.";
        }

        // ===== SESSION / PARTITIONAL SEARCH / THREAD COMPLETED =====
        if (lower.contains("session closed")) {
            String thread = extractBracketAfter(msg, "session closed");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Une session technique de recherche ou de chargement est fermée"
                    + valuePart(". Thread", thread)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ".";
        }

        if (lower.contains("start partitional searchcomposantbyroot")) {
            String thread = extractBracketAfter(msg, "searchComposantByRoot");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);

            return "Le système démarre une recherche partitionnée"
                    + valuePart(". Thread", thread)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + ". Cette recherche est divisée en parties pour traiter un volume important.";
        }

        if (lower.contains("end partitional searchcomposantbyroot")) {
            String thread = extractBracketAfter(msg, "searchComposantByRoot");
            String filter = extractBracket(msg, "filter code");
            String uuid = extractUuid(msg);
            String duration = extractDuration(msg);

            return "Le système termine une recherche partitionnée"
                    + valuePart(". Thread", thread)
                    + valuePart(". Filtre", filter)
                    + valuePart(". uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("the thread") && lower.contains("is completed")) {
            String thread = extractBracketAfter(msg, "the thread");
            String size = extract(msg, "finalResult is\\s*(\\d+)\\s*row");

            String volume = "";
            if (parseLong(size) >= 100000) {
                volume = " Le volume retourné est très élevé et peut expliquer une consommation mémoire importante ou une lenteur.";
            }

            return "Un thread de recherche est terminé"
                    + valuePart(". Thread", thread)
                    + valuePart(". Taille du résultat final", size, " ligne(s)")
                    + "." + volume;
        }

        // ===== TRACABILITE ALGESIRAS =====
        if (lower.contains("ws_findtracabilitealgesiras") && lower.contains("start")) {
            return "Le système démarre la recherche de traçabilité Algesiras. "
                    + "Cette opération reconstruit le parcours de l’unité à partir des services, checkpoints et événements de traçabilité.";
        }

        if (lower.contains("end ws_findtracabilitealgesiras")) {
            String duration = extractDuration(msg);

            return "La recherche de traçabilité Algesiras est terminée. "
                    + "Le système a récupéré les services associés et les checkpoints du parcours."
                    + durationPart(duration);
        }

        if (lower.contains("listservices") && lower.matches(".*listservices\\s*\\*+\\s*\\d+.*")) {
            String count = extract(msg, "listServices\\s*\\*+\\s*(\\d+)");

            return "Le système lit la liste des services trouvés"
                    + valuePart(". Nombre de services", count)
                    + ".";
        }
        // ====================== SAVE / PERSIST GENERIQUE =====
        if (lower.contains("saveprocesscontent")) {
            return "Le système enregistre le contenu du processus en base de données.";
        }

        // ====================== WORKFLOW / JBPM =====
        if (lower.contains("start transittask")) {
            return "Le workflow démarre une transition vers une nouvelle étape (task) dans le processus métier.";
        }

        if (lower.contains("createjbpmcontext")) {
            return "Le système initialise le contexte d’exécution du workflow JBPM pour gérer le processus métier.";
        }

        if (lower.contains("load workprocessinstance")) {
            return "Le système charge une instance existante de workflow pour poursuivre son exécution.";
        }

        // ====================== DOCUMENT / IMPRESSION =====
        if (lower.contains("document") && lower.contains("starting the conversion")) {
            return "Le document est en cours de conversion vers un format imprimable ou exportable.";
        }

        if (lower.contains("document") && lower.contains("start printing")) {
            return "Le système démarre l’impression du document.";
        }

        if (lower.contains("file sent to http") && lower.contains("jobid")) {
            return "Le document a été envoyé à une imprimante réseau avec un identifiant de tâche (job ID).";
        }

        if (lower.contains("cups server")) {
            return "Le système utilise le serveur CUPS pour gérer l’impression.";
        }

        if (lower.contains("printer")) {
            return "Le document est envoyé vers une imprimante spécifique.";
        }

        // ====================== STATUS =====
        if (lower.contains("current status = processing")) {
            return "Le système indique que le traitement est en cours.";
        }

        if (lower.contains("status after") && lower.contains("processing")) {
            return "Le traitement est toujours en cours après un certain délai.";
        }

        // ====================== VIRTUAL HOST =====
        if (lower.contains("virtualhost requestservername")) {
            return "Le serveur traite une requête HTTP en utilisant une configuration VirtualHost spécifique.";
        }

        if (lower.contains("virtualhost requestheaderhost")) {
            return "Le système récupère l’en-tête HTTP Host de la requête.";
        }

        if (lower.contains("virtualhost inethostaddress")) {
            return "Adresse IP du serveur traitant la requête.";
        }

        if (lower.contains("virtualhost inethostname")) {
            return "Nom du serveur traitant la requête.";
        }

        if (lower.contains("virtualhost last used")) {
            return "Dernier serveur utilisé pour traiter la requête.";
        }

        // ====================== PORTAL =====
        if (lower.contains("portalmain") && lower.contains("loading portal")) {
            return "Le portail applicatif est en cours de chargement.";
        }

        // ====================== MAJ / INSERT =====
        if (lower.contains("maj_insert_dynascreen")) {
            if (lower.contains("start")) {
                return "Début de l’insertion dynamique d’un écran dans le système.";
            }
            if (lower.contains("services not null")) {
                return "Les services nécessaires à l’insertion sont correctement initialisés.";
            }
            if (lower.contains("inserted")) {
                return "L’écran a été inséré avec succès.";
            }
            if (lower.contains("end")) {
                return "Fin du processus d’insertion de l’écran.";
            }
        }

        if (lower.contains("maj_insert_traceampe")) {
            return "Le système enregistre une trace métier liée à une opération AMPE.";
        }

        // ====================== TRACABILITE =====
        if (lower.contains("start insertintracabilite")) {
            return "Début de l’insertion d’une trace de traçabilité.";
        }

        if (lower.contains("end insertintracabilite")) {
            return "Fin de l’insertion de la trace de traçabilité.";
        }

        if (lower.contains("insertintracabilite")) {
            return "Insertion d’une trace de traçabilité dans le système pour suivi des opérations.";
        }

        // ====================== USER / LOGIN =====
        if (lower.contains("loadpersonnephysiquebylogin")) {
            return "Le système charge les informations d’un utilisateur à partir de son login.";
        }

        if (lower.contains("login =")) {
            return "Identification de l’utilisateur en cours.";
        }

        // ====================== DEBUG SPECIFIQUE =====
        if (lower.contains("creation message")) {
            return "Création d’un message interne pour traitement ou log.";
        }

        if (lower.contains("list not empty")) {
            return "Une liste de données contient des éléments exploitables.";
        }

        // ===== RETURN NULL : GENERIC FALLBACK =====
        if (lower.contains("return null")) {
            return "Une méthode de structuration se termine avec une valeur null. "
                    + "Cela signifie qu’aucune donnée exploitable n’a été produite pour cette étape, "
                    + "probablement parce que la valeur source est vide, absente ou non reconnue.";
        }
        // ===== ADDITIONAL WORKS PROCESS / AMPI / FXCRUD / PRINT =====

// ===== COMPILED PACKAGE / RULE CACHE =====
        if (lower.contains("compiledpackage") && lower.contains("regleids")) {
            String uuid = extractUuid(msg);
            String regleIds = extract(msg, "regleIds\\s*(\\d+)");
            String compiledPackage = extract(msg, "compiledPackage\\s*(\\d+)");

            return "Le système utilise un package compilé de règles métier"
                    + valuePart(". uuid", uuid)
                    + valuePart(". Groupe de règles", regleIds)
                    + valuePart(". Package compilé", compiledPackage)
                    + ". Le moteur charge une version optimisée des règles pour accélérer leur exécution.";
        }

        if (lower.contains("update cachename") && lower.contains("compilepackagecache")) {
            String server = extract(msg, "serverName\\s*:\\s*([^\\s\\|]+)");

            return "Le cache des règles métier est mis à jour"
                    + valuePart(". Serveur", server)
                    + ". Cela permet d'utiliser les dernières versions des règles compilées.";
        }

// ===== VIRTUAL HOST =====
        if (lower.contains("virtualhost dp")) {
            String dp = extractBracketAfter(msg, "VirtualHost DP");

            return "Lecture du paramètre VirtualHost"
                    + valuePart(". DP", dp)
                    + ". Ce paramètre détermine le contexte serveur utilisé.";
        }

// ===== ICE DUM / AMP =====
        if (lower.contains("icedum") && lower.contains("amp")) {
            String ice = extract(msg, "iceDum\\s*[:=]\\s*([^\\s\\|]+)");
            String amp = extract(msg, "amp\\s*[:=]\\s*([^\\s\\|]+)");

            return "Lecture des informations douanières"
                    + valuePart(". ICE DUM", ice)
                    + valuePart(". AMP", amp)
                    + ". Ces données servent à identifier le dossier douanier.";
        }

// ===== PRINT VISIT =====
        if (lower.contains("checkinfovisitforprint") && lower.contains("start")) {
            return "Début de vérification des données avant impression. "
                    + "Le système contrôle que toutes les informations nécessaires sont présentes.";
        }

        if (lower.contains("checkinfovisitforprint") && lower.contains("end")) {
            return "Fin de vérification des données avant impression. "
                    + "Le document peut être généré.";
        }

// ===== FXCRUD =====
        if (lower.contains("fxcrud") && lower.contains("query search by root")) {
            String uuid = extractUuid(msg);

            return "FxCRUD prépare une requête métier"
                    + valuePart(". uuid", uuid)
                    + ". Cette requête permet de charger les objets liés.";
        }

        if (lower.contains("fxcrud") && lower.contains("values:")) {
            String values = extract(msg, "values:\\s*\\[([^\\]]+)]");

            return "Paramètres de la requête FxCRUD"
                    + valuePart(". Valeurs", values)
                    + ". Ces paramètres sont utilisés pour filtrer les résultats.";
        }

        if (lower.contains("fxcrud") && lower.contains("rootids")) {
            String rootIds = extract(msg, "rootIds:\\s*\\[([^\\]]+)]");

            return "FxCRUD utilise des identifiants racines"
                    + valuePart(". rootIds", rootIds)
                    + ". Le système récupère les données associées.";
        }

// ===== LIST NOT EMPTY =====
        if (lower.contains("list not") && lower.contains("empty")) {
            return "Une liste de résultats contient des données. "
                    + "Le traitement continue normalement.";
        }

// ===== AMPI / BAD =====
        if (lower.contains("maj_ampi_bad_description")) {
            return lower.contains("start")
                    ? "Début de mise à jour de la description BAD liée à AMPI."
                    : "Fin de mise à jour de la description BAD.";
        }

        if (lower.contains("ctrl_liaison_ampi_bad")) {
            return lower.contains("start")
                    ? "Début du contrôle de liaison AMPI/BAD."
                    : "Fin du contrôle de liaison AMPI/BAD.";
        }

        if (lower.contains("ctrl_escale_ampi_bad")) {
            return lower.contains("start")
                    ? "Début du contrôle d’escale AMPI/BAD."
                    : "Fin du contrôle d’escale AMPI/BAD.";
        }

        if (lower.contains("maj_liaison_ampi_bad")) {
            return lower.contains("start")
                    ? "Début de mise à jour de la liaison AMPI/BAD."
                    : "Fin de mise à jour de la liaison AMPI/BAD.";
        }

// ===== STATUT BAD =====
        if (lower.contains("statutbad")) {
            String statut = extract(msg, "statutBAD\\s*-+\\s*([^\\s\\|]+)");

            return "Lecture du statut BAD"
                    + valuePart(". Statut", statut)
                    + ". Indique l’état métier du BAD.";
        }

// ===== STEPS =====
        if (lower.matches(".*-{5,}\\s*\\d+\\.\\d+\\s*-{5,}.*")) {
            String step = extract(msg, "-+\\s*(\\d+\\.\\d+)\\s*-+");

            return "Étape interne de traitement"
                    + valuePart(". Étape", step)
                    + ". Sert au suivi du workflow.";
        }

// ===== MEDHUB =====
        if (lower.contains("maj_emh_bulletin_medhub")) {
            return lower.contains("start")
                    ? "Début de mise à jour du bulletin d’entrée MedHub."
                    : "Fin de mise à jour du bulletin d’entrée MedHub.";
        }

        if (lower.contains("maj_smh_bulletin_medhub")) {
            return lower.contains("start")
                    ? "Début de mise à jour du bulletin de sortie MedHub."
                    : "Fin de mise à jour du bulletin de sortie MedHub.";
        }

// ===== AUTH PM =====
        if (lower.contains("setlistauthpms")) {
            return "Chargement des personnes morales autorisées. "
                    + "Le système récupère les partenaires accessibles.";
        }

// ===== PRINT STATUS =====
        if (lower.contains("current status = pending")) {
            return "Le job d’impression est en attente.";
        }

        if (lower.contains("status after") && lower.contains("pending")) {
            String seconds = extract(msg, "status after\\s*(\\d+)");

            return "Vérification du job d’impression"
                    + valuePart(". Délai", seconds, " sec")
                    + ". Toujours en attente.";
        }


        // =====================================================
        // ===== BUSINESS COVERAGE EXTENSION - GLOBAL RESTANTS =====
        // =====================================================

        // ===== CODE PM ISOLE =====
        if (lower.matches("^pm\\d+$")) {
            return "Le système affiche un code PM isolé. "
                    + "Ce code identifie une personne morale, un client, un partenaire ou un déclarant utilisé dans le traitement métier.";
        }

        // ===== TEST TRYPTIQUE =====
        if (lower.contains("test_tryptique")) {
            return "Le système vérifie la liste des tryptiques associés au dossier. "
                    + "La valeur null signifie qu’aucun tryptique n’a été trouvé ou chargé à cette étape.";
        }

        // ===== MAJ INSERT MAQ =====
        if (lower.contains("maj_insert_maq")) {
            String typeUnite = extract(msg, "typeUniteID\\s*:\\s*([^\\s\\|]+)");

            return "Le système prépare l’insertion MAQ"
                    + valuePart(". Type unité", typeUnite)
                    + ". Cette étape rattache le type d’unité, par exemple TIR ou conteneur, au traitement MAQ.";
        }

        // ===== ICE SIMPLE =====
        if (lower.contains("***** ice *****")) {
            String ice = extract(msg, "ice\\s*\\*+/?\\s*([^\\s\\|]+)");

            return "Le système lit l’ICE associé au dossier"
                    + valuePart(". ICE", ice)
                    + ". Une valeur null signifie qu’aucun ICE n’a été renseigné pour cette étape.";
        }

        // ===== HISTORIQUE PARKING / AMPI =====
        if (lower.contains("@parkingvalid")
                || lower.contains("@ampiv@validated")
                || lower.contains("@ampiprinted")
                || lower.contains("@invoicectrl")) {
            return "Le système affiche l’historique d’état du dossier AMPI. "
                    + "La chaîne contient les étapes métier déjà franchies, comme validation parking, validation AMPI, impression, contrôle export et facturation.";
        }

        // ===== TRANSVASEMENT =====
        if (lower.contains("notransvasement")) {
            String no = extract(msg, "noTransvasement\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le numéro de transvasement"
                    + valuePart(". Numéro transvasement", no)
                    + ". Si aucune valeur n’est présente, aucun transvasement n’est associé à cette étape.";
        }


        // ===== THREAD SAVE CONTAINER / PAYMENT =====
        if (lower.contains("start run thread") && lower.contains("save container")) {
            String objectName = extract(msg, "\\[\\s*(payCont)\\s*]");
            String objects = extract(msg, "for\\s*(\\d+)\\s*object");
            String rows = extract(msg, "rows\\s*between\\s*(\\d+\\s*and\\s*\\d+)");

            return "Le système démarre un thread de sauvegarde de conteneur lié au paiement"
                    + valuePart(". Objet", objectName)
                    + valuePart(". Nombre d’objets", objects)
                    + valuePart(". Plage de lignes", rows)
                    + ". Cette étape traite les conteneurs par lots pour accélérer l’enregistrement.";
        }

        // ===== THREAD PAYMENT COMPLETED =====
        if (lower.contains("this thread") && lower.contains("ws_pay") && lower.contains("is completed")) {
            String code = extract(msg, "code\\s*:\\s*([^\\s]+)");
            String threadId = extract(msg, "threadId\\s*:\\s*([^\\s]+)");

            return "Un thread de traitement paiement s’est terminé correctement"
                    + valuePart(". Code WS", code)
                    + valuePart(". Thread", threadId)
                    + ". Cette ligne confirme la fin d’un traitement parallèle.";
        }

        // ===== ALL THREADS PAYMENT COMPLETED =====
        if (lower.contains("codews") && lower.contains("ws_pay") && lower.contains("finished all threads")) {
            String code = extractBracket(msg, "codeWS");
            String pool = extractBracket(msg, "Finished all threads");

            if (lower.contains("rerun corrupted threads")) {
                return "Le système a terminé tous les threads du traitement paiement puis a relancé les threads corrompus si nécessaire"
                        + valuePart(". Code WS", code)
                        + valuePart(". Pool", pool)
                        + ". Cette ligne confirme que le traitement parallèle est finalisé avec contrôle de récupération.";
            }

            return "Le système a terminé tous les threads du traitement paiement"
                    + valuePart(". Code WS", code)
                    + valuePart(". Pool", pool)
                    + ". Cette ligne confirme la fin complète du traitement parallèle.";
        }

        // ===== CACHE COMPOSANT DEFINITION =====
        if (lower.contains("update cachename") && lower.contains("composantdefinitionclasscache")) {
            String server = extract(msg, "serverName\\s*:\\s*([^\\s\\|]+)");

            return "Le cache des définitions de classes de composants est mis à jour"
                    + valuePart(". Serveur", server)
                    + ". Cette opération permet au système d’utiliser les dernières définitions de composants.";
        }

        // ===== DEBUG NUMERIQUE SIMPLE =====
        if (lower.matches("^\\*{3,}\\s*\\d+\\s*\\*{3,}.*")) {
            String step = extract(msg, "\\*+\\s*(\\d+)\\s*\\*+");
            String date = extract(msg, "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)");

            return "Le système affiche une étape numérique de debug"
                    + valuePart(". Étape", step)
                    + valuePart(". Date associée", date)
                    + ". Cette ligne sert au suivi interne du traitement.";
        }

        if (lower.equals("null")) {
            return "Le système affiche une valeur nulle. Cela signifie qu’aucune donnée n’a été trouvée ou renseignée pour ce point du traitement.";
        }

        if (lower.matches("^[a]+$")) {
            return "Le système affiche une ligne de debug technique répétitive. Elle ne représente pas une action métier.";
        }

        if (lower.matches("^\\d+$")) {
            return "Le système affiche une valeur numérique isolée utilisée comme compteur, étape ou identifiant interne.";
        }
        // ===== PAYMENT / FACTURATION =====
        if (lower.contains("ws_insertpayment")) {
            String step = extract(msg, "WS_InsertPayment_(\\d+)");
            String amount = extract(msg, "-+\\s*(\\d+)\\s*-+$");

            return "Le système traite une insertion de paiement"
                    + valuePart(". Étape", step)
                    + valuePart(". Valeur", amount)
                    + ". Cette ligne appartient au flux de paiement ou facturation.";
        }

        if (lower.contains("noinvoice")) {
            String invoice = extract(msg, "noInvoice[-\\*]*\\s*([A-Za-z0-9]+)");

            return "Le système lit le numéro de facture"
                    + valuePart(". Facture", invoice)
                    + ". Cette information est utilisée dans le traitement de paiement ou de facturation.";
        }

        if (lower.contains("client----------------")) {
            String client = extract(msg, "client-+\\s*([^\\s\\|]+)");

            return "Le système lit le client concerné par la facturation"
                    + valuePart(". Client", client)
                    + ".";
        }

        if (lower.contains("refpay")) {
            String ref = extract(msg, "::\\s*([^\\s\\|]+)");

            return "Le système lit la référence de paiement Fatourati"
                    + valuePart(". Référence paiement", ref)
                    + ".";
        }

        if (lower.contains("nopay")) {
            String noPay = extract(msg, "::\\s*([^\\s\\|]+)");

            return "Le système lit le numéro de paiement"
                    + valuePart(". Numéro paiement", noPay)
                    + ".";
        }

        if (lower.contains("flagtable_cube_invoice_impaye")) {
            return "Le système démarre le marquage de la table des factures impayées. Cette étape prépare ou signale le traitement des factures non réglées.";
        }

        if (lower.contains("start for _invoices")) {
            return "Début de la boucle de traitement des factures.";
        }

        if (lower.contains("end for _invoices")) {
            return "Fin de la boucle de traitement des factures.";
        }

        if (lower.contains("wsloadinvoiceportail")) {
            if (lower.contains("loaded params")) {
                return "Le système a chargé les paramètres nécessaires pour récupérer les factures depuis le portail.";
            }
            if (lower.contains("ok")) {
                return "Le chargement des factures depuis le portail s’est terminé correctement.";
            }
        }
        // ===== BLM ENTREE / SORTIE =====
        if (lower.contains("ws_save_blm_entreesortie")) {
            String ref = extract(msg, "refBulletin=([^\\s\\|]+)");
            String unites = extract(msg, "unites=(\\d+)");
            String lignes = extract(msg, "lignesDemande=(\\d+)");

            if (lower.contains("start")) {
                return "Début de la sauvegarde du bulletin entrée/sortie BLM.";
            }

            return "Le système sauvegarde un bulletin entrée/sortie BLM"
                    + valuePart(". Référence bulletin", ref)
                    + valuePart(". Unités", unites)
                    + valuePart(". Lignes demande", lignes)
                    + ". Cette opération met à jour les informations de passage ou de sortie.";
        }
        // ===== UPDATE ADMIN SERVICES =====
        if (lower.contains("updateadmset_services")) {
            String step = extract(msg, "UpdateAdmSet_Services\\s*-+\\s*([^\\s\\|]+)");
            String status = extract(msg, "/\\s*(.+)$");

            if (lower.contains("saved sp list")) {
                return "Le système a sauvegardé la liste des services portuaires après mise à jour administrative.";
            }

            if (lower.contains("retrieved services")) {
                String count = extract(msg, "dums\\s*:\\s*(\\d+)");
                return "Le système récupère les services/DUM concernés par la mise à jour administrative"
                        + valuePart(". Nombre trouvé", count) + ".";
            }

            return "Le système met à jour le statut administratif des services"
                    + valuePart(". Étape", step)
                    + valuePart(". Contrôles associés", status)
                    + ".";
        }

        if (lower.contains("statutadm before")) {
            String ref = extract(msg, "before\\s*:\\s*([^\\s\\|]+)");
            String statut = extract(msg, "-\\s*(S\\d+)\\|");

            return "Le système lit le statut administratif avant modification"
                    + valuePart(". Référence", ref)
                    + valuePart(". Statut initial", statut)
                    + ".";
        }
        // ===== ICE / DECLARANT / IMPORTATEUR =====
        if (lower.contains("iceimportateur")) {
            String ice = extractBracketAfter(msg, "iceImportateur");

            return "Le système lit l’ICE de l’importateur"
                    + valuePart(". ICE importateur", ice)
                    + ". Cet identifiant fiscal permet de rattacher le dossier à l’entreprise importatrice.";
        }

        if (lower.contains("ice existe avec le code")) {
            String code = extract(msg, "code\\s*-+\\s*([^\\s\\|]+)");

            return "Le système confirme l’existence de l’ICE avec le code"
                    + valuePart(". Code", code)
                    + ".";
        }

        if (lower.contains("codedeclarant")) {
            String code = extract(msg, "codeDeclarant[-\\*]*\\s*([^\\s\\|]+)");

            return "Le système lit le code déclarant"
                    + valuePart(". Code déclarant", code)
                    + ". Cette donnée identifie le déclarant douanier.";
        }

        if (lower.contains("declarantdouane")) {
            String code = extract(msg, "declarantDouane-+\\s*([^\\s\\|]+)");

            return "Le système lit le déclarant douane"
                    + valuePart(". Déclarant", code)
                    + ".";
        }

        if (lower.contains("codedeclarantportail")) {
            String code = extract(msg, "codeDeclarantPortail\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le code déclarant portail"
                    + valuePart(". Code portail", code)
                    + ".";
        }
        // ===== CONTREECOR / CTCA / DUM =====
        if (lower.contains("ws_insert_contreecor")) {
            String ref = extract(msg, "WS_Insert_Contreecor\\s*-+\\s*([A-Za-z0-9]+)");

            if (lower.contains("start")) {
                return "Début de l’insertion Contreecor. Le système prépare l’enregistrement ou la synchronisation du dossier.";
            }

            return "Le système traite une insertion Contreecor"
                    + valuePart(". Référence", ref)
                    + ".";
        }

        if (lower.contains("ws_insert_ctca")) {
            String dum = extract(msg, "\"referenceDUM\"\\s*:\\s*\"([^\"]+)\"");
            String type = extract(msg, "\"type\"\\s*:\\s*\"([^\"]+)\"");

            return "Le système reçoit un message CTCA depuis ADII"
                    + valuePart(". Référence DUM", dum)
                    + valuePart(". Type", type)
                    + ". Ce message correspond à une information douanière échangée avec TMSA.";
        }

        if (lower.contains("refgroupage")) {
            String ref = extract(msg, "refGroupage\\s*-+\\s*([^\\s\\|]+)");

            return "Le système lit la référence de groupage"
                    + valuePart(". Référence groupage", ref)
                    + ".";
        }

        if (lower.contains("idautorisation")) {
            String id = extract(msg, "IDAUTORISATION\\s*-+\\s*([^\\s\\|]+)");

            return "Le système lit l’identifiant d’autorisation"
                    + valuePart(". ID autorisation", id)
                    + ".";
        }

        if (lower.contains("listdum")) {
            String count = extract(msg, "listDUM\\s*\\*+\\s*(\\d+)");

            return "Le système lit la liste des DUM associées"
                    + valuePart(". Nombre", count)
                    + ".";
        }
        // ===== ZALTER / ALTER AMP =====
        if (lower.contains("zalter") || lower.contains("ws_alter_amp") || lower.contains("ws_alter_aap")) {
            if (lower.contains("ws_alter_aap") && lower.contains("etat")) {
                String etat = extract(msg, "etat\\s*=\\s*([^\\s\\|]+)");
                return "Le système modifie l’état AAP"
                        + valuePart(". État", etat)
                        + ".";
            }

            String value = extract(msg, "ZALTER.*?_+\\s*([^\\s_]+)\\s*$");

            return "Le système exécute ou trace une modification AMP/AAP"
                    + valuePart(". Valeur", value)
                    + ". Cette ligne appartient au flux d’altération ou de mise à jour du dossier.";
        }
        // ===== PDA / VISITE PHYSIQUE / SCANNER =====
        if (lower.contains("ws_find_unite_pda")) {
            String code = extract(msg, "Code Client\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche une unité PDA"
                    + valuePart(". Code client", code)
                    + ". Cette recherche sert à retrouver l’unité pour un traitement opérationnel.";
        }

        if (lower.contains("ws_visite_physique_load_info")) {
            String sp = extract(msg, "==>\\s*([^\\s\\|]+)");

            return "Le système charge les informations de visite physique"
                    + valuePart(". Service portuaire", sp)
                    + ".";
        }

        if (lower.contains("entree_scanner_import")
                || lower.contains("sortie_terminal_import")
                || lower.contains("debut_visite_physique")
                || lower.contains("fin_visite_physique")
                || lower.contains("maq_import")
                || lower.contains("maq_export")) {
            String event = extract(msg, ":\\s*([A-Z_]+)$");

            return "Le système trace une étape opérationnelle du dossier"
                    + valuePart(". Étape", event)
                    + ". Cette étape peut concerner scanner, visite physique, terminal ou MAQ.";
        }
        // ===== AMP / AMPI STATE STRING =====
        if (lower.contains("@ampicreated") || lower.contains("allstate")) {
            String ed = extract(msg, "(ED\\d+)");
            String tc = extract(msg, "(TC\\d+)");
            String unit = ed != null ? ed : tc;

            return "Le système affiche l’historique complet d’état d’un dossier AMPI/AMP"
                    + valuePart(". Unité ou dossier", unit)
                    + ". La chaîne contient les étapes comme création, validation, impression, contrôle export, facturation, fermeture et parking validé.";
        }

        if (lower.contains("etat_demat_valide")) {
            return "Le système indique que le dossier est à l’état dématérialisé validé.";
        }

        if (lower.contains("etat_demat_save")) {
            return "Le système indique que le dossier est sauvegardé dans le processus de dématérialisation.";
        }

        if (lower.contains("etat_demat_rejet")) {
            return "Le système indique que le dossier est rejeté dans le processus de dématérialisation.";
        }

        if (lower.contains("etatenregistrement")) {
            String etat = extract(msg, "(ETAT_[A-Z_]+)");

            return "Le système lit l’état d’enregistrement"
                    + valuePart(". État", etat)
                    + ".";
        }
        // ===== DOCUMENT / DOWNLOAD / DOC AMP =====
        if (lower.contains("docinstanceid")) {
            String id = extract(msg, "docInstanceId\\s*:\\s*(\\d+)");

            return "Le système lit l’identifiant d’instance documentaire"
                    + valuePart(". Document ID", id)
                    + ".";
        }

        if (lower.contains("downloaded with no errors")) {
            String id = extract(msg, "id\\s*:\\s*(\\d+)");

            return "Le fichier a été téléchargé sans erreur"
                    + valuePart(". Fichier ID", id)
                    + ".";
        }

        if (lower.contains("ws_insert_amp_doc")) {
            if (lower.contains("ok")) {
                return "Le document AMP a été inséré correctement.";
            }

            String newType = extract(msg, "boWSDel\\.typeDocument=([^\\s]+)");
            String oldType = extract(msg, "oldDoc\\.typeDocument=([^\\s]+)");

            return "Le système insère ou remplace un document AMP"
                    + valuePart(". Nouveau type document", newType)
                    + valuePart(". Ancien type document", oldType)
                    + ".";
        }

        if (lower.contains("listdocuments")) {
            String count = extract(msg, "(\\d+)$");

            return "Le système récupère la liste des documents associés au dossier"
                    + valuePart(". Nombre de documents", count)
                    + ".";
        }
        // ===== EQUIPEMENTS / UNITE / SERVICE =====
        if (lower.contains("equipementreference")) {
            String ref = extract(msg, "EQUIPEMENTREFERENCE\\s*-+\\s*([^\\s\\|]+)");

            return "Le système lit la référence de l’équipement"
                    + valuePart(". Équipement", ref)
                    + ". Cette référence identifie un conteneur, véhicule ou unité logistique.";
        }

        if (lower.contains("typeunitelib")) {
            String type = extract(msg, "typeUniteLib\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le libellé du type d’unité"
                    + valuePart(". Type unité", type)
                    + ".";
        }

        if (lower.contains("typesunites")) {
            String count = extract(msg, "typesUnites\\s*\\*+\\s*(\\d+)");

            return "Le système lit le nombre de types d’unités disponibles"
                    + valuePart(". Nombre", count)
                    + ".";
        }

        if (lower.contains("nomservice")) {
            String nom = extract(msg, "nomService\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le nom du service"
                    + valuePart(". Service", nom)
                    + ".";
        }

        if (lower.contains("noentree")) {
            String no = extract(msg, "noEntree\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le numéro d’entrée"
                    + valuePart(". Numéro entrée", no)
                    + ".";
        }

        if (lower.contains("nomanutention")) {
            String no = extract(msg, "noManutention\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le numéro de manutention"
                    + valuePart(". Numéro manutention", no)
                    + ".";
        }
        // ===== EMH / BPARTNER =====
        if (lower.contains("ws_save_emh")) {
            String code = extract(msg, "\"code\"\\s*:\\s*\"([^\"]+)\"");
            String raw = extract(msg, "U\\*([^\\*]+)\\*");

            if (lower.contains("start bpartner")) {
                return "Début de sauvegarde du partenaire EMH"
                        + valuePart(". Référence", raw)
                        + ".";
            }

            if (lower.contains("end bpartner")) {
                return "Fin de sauvegarde du partenaire EMH"
                        + valuePart(". Code partenaire", code != null ? code : raw)
                        + ".";
            }

            return "Le système sauvegarde ou met à jour un partenaire EMH.";
        }

        if (lower.contains("save emh start")) {
            String ref = extract(msg, "U\\*([^\\*]+)\\*");

            return "Début de sauvegarde EMH"
                    + valuePart(". Référence", ref)
                    + ".";
        }

        if (lower.contains("emh : brouillon")) {
            String ref = extract(msg, "U\\*([^\\*]+)\\*");

            return "Le système traite un brouillon EMH"
                    + valuePart(". Référence", ref)
                    + ".";
        }

        if (lower.contains("emh updated")) {
            String ref = extract(msg, "U\\*([^\\*]+)\\*");

            return "Le système confirme la mise à jour EMH"
                    + valuePart(". Référence", ref)
                    + ".";
        }

        if (lower.contains("ws_find_emh")) {
            String code = extract(msg, "Code Client\\s*=\\s*([^\\s\\|]+)");

            return "Le système recherche un dossier ou partenaire EMH"
                    + valuePart(". Code client", code)
                    + ".";
        }
        // ===== MARITIME / VESSEL / ML =====
        if (lower.contains("ws_save_vessel_moves")) {
            if (lower.contains("start")) {
                return "Début de sauvegarde des mouvements navire.";
            }

            String id = extract(msg, "idMessage=\\s*([^\\s\\|]+)");

            return "Le système traite un message de mouvement navire"
                    + valuePart(". Message ID", id)
                    + ".";
        }

        if (lower.contains("dateannulationml")) {
            String date = extract(msg, "dateAnnulationML\\s*\\*+\\s*(.+)$");

            return "Le système lit la date d’annulation ML"
                    + valuePart(". Date annulation", date)
                    + ".";
        }

        if (lower.contains("the content of newml")) {
            String dum = extract(msg, "\"referenceDUM\"\\s*:\\s*\"([^\"]+)\"");
            String id = extract(msg, "\"id\"\\s*:\\s*(\\d+)");

            return "Le système affiche le contenu du nouvel objet ML"
                    + valuePart(". Référence DUM", dum)
                    + valuePart(". ID", id)
                    + ".";
        }
        // ===== CHAMPS SIMPLES RESTANTS =====
        if (lower.contains("datefin")) {
            String date = extract(msg, "dateFin\\s*\\*+\\s*(.+)$");

            return "Le système lit une date de fin"
                    + valuePart(". Date fin", date)
                    + ".";
        }

        if (lower.contains("dateout")) {
            String date = extract(msg, "dateOut\\s*\\*+\\s*(.+)$");

            return "Le système lit une date de sortie"
                    + valuePart(". Date sortie", date)
                    + ". Une valeur null signifie qu’aucune date de sortie n’est encore renseignée.";
        }

        if (lower.contains("transporteur")) {
            String transporteur = extract(msg, "transporteur\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le transporteur"
                    + valuePart(". Transporteur", transporteur)
                    + ".";
        }

        if (lower.contains("maxresults")) {
            String max = extract(msg, "maxResults\\s*\\*+\\s*(\\d+)");

            return "Le système lit la limite maximale de résultats"
                    + valuePart(". Maximum", max)
                    + ".";
        }

        if (lower.contains("thread corrupu") || lower.contains("thread corrompu")) {
            String code = extractBracketAfter(msg, "code");

            return "Le système vérifie l’existence d’un thread corrompu"
                    + valuePart(". Code", code)
                    + ". Aucun thread corrompu n’a été trouvé pour ce code.";
        }

        // ===== ADDITIONAL PROCESS / TEMP TABLE / BAD / DATA STRUCTURE =====
        if (lower.contains("create global temporary table")) {
            String uuid = extractBracket(msg, "uuid");
            String callId = extractBracket(msg, "callId");

            return "Le système crée une table temporaire en base PostgreSQL"
                    + valuePart(". uuid", uuid)
                    + valuePart(". Appel", callId)
                    + ". Cette table sert à stocker des données intermédiaires pendant le traitement, "
                    + "afin d’optimiser les performances et éviter de recalculer certaines étapes.";
        }

        if (lower.matches("^[\\*\\-]{5,}.*[\\*\\-]{5,}$")) {
            return "Ce log est un séparateur visuel utilisé pour structurer ou déboguer les traces. "
                    + "Il ne correspond pas à une action métier mais aide à lire les logs.";
        }

        if (lower.contains("after start")) {
            String step = extract(msg, "after start\\s*(\\d+)");

            return "Le système indique une étape intermédiaire du traitement"
                    + valuePart(". Étape", step)
                    + ". Ce log sert à suivre la progression interne.";
        }

        if (lower.contains("servicecourant")) {
            String service = extract(msg, "serviceCourant\\s*[:=\\-]*\\s*([^\\s\\|]+)");

            return "Le système traite le service courant"
                    + valuePart(". Service", service)
                    + ". Ce log permet d’identifier l’entité métier en cours de traitement.";
        }

        if (lower.contains("msgfr")) {
            return "Le système prépare un message utilisateur (SMS ou affichage écran). "
                    + "Ce message est destiné à guider l’utilisateur dans le processus métier.";
        }

        if (lower.contains("map") && lower.contains("{")) {
            return "Le système affiche les données structurées sous forme de Map (clé/valeur). "
                    + "Ces informations correspondent à des données métier (chauffeur, service, etc.) utilisées dans le traitement.";
        }

        if (lower.contains("resultat")) {
            String result = extract(msg, "(?i)resultat.*?(NS|S|null)");

            return "Le système indique le résultat d’une opération"
                    + valuePart(". Résultat", result)
                    + ". Cela peut représenter un statut métier (succès, non satisfait, ou vide).";
        }

        if (lower.contains("scan_operation") || lower.contains("liberation_")) {
            if (lower.contains("start")) {
                return "Début d’un traitement de scan ou de libération d’une opération. "
                        + "Le système commence l’analyse ou la validation liée au contrôle.";
            }
            if (lower.contains("end")) {
                return "Fin du traitement de scan ou de libération.";
            }
        }

        if (lower.contains("ws_search_badeq")) {
            if (lower.contains("criteria")) {
                return "Le système prépare les critères de recherche d’un BAD.";
            }

            if (lower.contains("refbad")) {
                return "Le système utilise une référence BAD pour effectuer une recherche.";
            }

            if (lower.contains("found")) {
                String count = extract(msg, "found\\s*=\\s*(\\d+)");

                return "Résultat de la recherche BAD"
                        + valuePart(". Nombre trouvé", count)
                        + ". Indique si des données ont été trouvées.";
            }

            return "Le système exécute une recherche BAD (dossier administratif).";
        }

        if (lower.contains("update") && lower.contains("set") && lower.contains("where")) {
            return "Le système exécute une requête de mise à jour en base de données. "
                    + "Cela modifie l’état ou les informations d’un enregistrement.";
        }

        if (lower.contains("devise_")) {
            String devise = extract(msg, "(DEVISE_[A-Z]+)");

            return "Le système lit la devise utilisée"
                    + valuePart(". Devise", devise)
                    + ". Cette information est utilisée pour les calculs financiers.";
        }

        if (lower.contains("condition")) {
            return "Le moteur de règles évalue une condition logique pour décider de la suite du traitement.";
        }

        if (lower.contains("ice existe")) {
            return "Le système vérifie l’existence d’un ICE (identifiant fiscal).";
        }

        if (lower.contains("update bpartner")) {
            return "Le système met à jour les informations d’un partenaire (client, entreprise).";
        }

        if (lower.contains("pesage")) {
            return "Le système traite une opération de pesage (poids du véhicule ou marchandise).";
        }

        if (lower.contains("cancel")) {
            return "Le système annule une opération métier en cours.";
        }

        if (lower.contains("structure_interface_data")) {
            return "Le système structure les données d’interface en objets métier. "
                    + "Cette étape transforme les données brutes en format exploitable.";
        }

        if (lower.contains("size")) {
            String size = extractBracket(msg, "size");

            return "Le système indique le nombre d’éléments récupérés"
                    + valuePart(". Taille", size)
                    + ". Ce log permet de vérifier si des données ont été trouvées.";
        }

        // ====================== PERFORMANCE / TEMPS : GENERIC FALLBACK =====
        if ((lower.contains("took") && lower.contains("ms"))
                || lower.contains("took [")
                || lower.contains("in host took [")
                || lower.matches(".*took\\s+\\d+\\s*ms.*")
                || lower.matches(".*took\\s*\\d+\\s*\\(ms\\).*")
                || lower.matches(".*took\\(ms\\)=\\d+.*")) {

            if (lower.contains("create component zscript")) {
                return "Le système génère dynamiquement un composant d’interface utilisateur (ZK/Zscript) pour un écran donné. "
                        + "Cela correspond à la construction de l’UI côté serveur.";
            }

            if (lower.contains("loadoperationcontext")) {
                return "Le système charge le contexte d’une opération métier (données nécessaires à l’exécution).";
            }

            if (lower.contains("loadfilsoperation")) {
                return "Le système charge les opérations enfants liées à une opération principale.";
            }

            if (lower.contains("loadoperationbyid")) {
                return "Le système récupère une opération métier spécifique à partir de son identifiant.";
            }

            if (lower.contains("convert list")) {
                return "Le système transforme une liste de données d’un format à un autre pour traitement interne.";
            }

            if (lower.contains("call to service")) {
                return "Un appel a été effectué vers un service externe ou interne pour récupérer ou traiter des données.";
            }

            String duration = extractDuration(msg);
            long ms = parseLong(duration);
            String severity = ms >= 5000 ? " Cette durée est élevée et peut indiquer une lenteur."
                    : ms >= 2000 ? " Cette durée mérite une vérification."
                      : " Cette durée semble acceptable.";

            return "Une opération technique a été exécutée avec mesure du temps d’exécution (performance)."
                    + durationPart(duration)
                    + severity;
        }
        // ===== YOUNES SP / DUMSET =====
        if (lower.contains("younes") && lower.contains("dumset")) {
            String sp = extract(msg, "SP\\s*=\\s*([^\\s\\|]+)");
            String dum = extract(msg, "DumSet\\s*=\\s*/\\s*([^\\s<]+)");
            String type = extract(msg, "<([^>]+)>");

            return "Le système affiche le rattachement entre un service portuaire et une DUM"
                    + valuePart(". Service portuaire", sp)
                    + valuePart(". DUM", dum)
                    + valuePart(". Type déclaration", type)
                    + ". Cette ligne permet de vérifier si la déclaration associée au service est normale ou provisionnelle.";
        }

// ===== AMPI BAD TRACTION =====
        if (lower.contains("maj_codesecurite_ampi_bad_traction")) {
            if (lower.contains("start")) {
                return "Début de la mise à jour du code de sécurité AMPI/BAD Traction. "
                        + "Le système prépare le code sécurité lié à l’opération de traction.";
            }
            if (lower.contains("end")) {
                return "Fin de la mise à jour du code de sécurité AMPI/BAD Traction.";
            }
        }

        if (lower.contains("maj_ampi_bad_blockchain_traction")) {
            if (lower.contains("start")) {
                return "Début de la mise à jour blockchain AMPI/BAD Traction. "
                        + "Le système trace ou synchronise les informations BAD liées à la traction.";
            }
            if (lower.contains("end")) {
                return "Fin de la mise à jour blockchain AMPI/BAD Traction.";
            }
        }

// ===== CONTROLE COMMANDE TRACTION DEMAT =====
        if (lower.contains("ctrlcmdtraction_demat")) {
            if (lower.contains("start")) {
                return "Début du contrôle de commande de traction dématérialisée. "
                        + "Le système vérifie la cohérence des données avant de poursuivre le traitement.";
            }
            if (lower.contains("end")) {
                return "Fin du contrôle de commande de traction dématérialisée.";
            }
        }

// ===== LISTBOX SELECTION =====
        if (lower.contains("listbox") && lower.contains("mapping") && lower.contains("nbrselectedobject with ids")) {
            String listbox = extractBracket(msg, "listbox");
            String mapping = extractBracket(msg, "mapping");
            String count = extractBracket(msg, "nbrSelectedObject with ids");

            return "Le système lit une sélection dans une liste de l’interface"
                    + valuePart(". Liste", listbox)
                    + valuePart(". Mapping", mapping)
                    + valuePart(". Identifiants sélectionnés", count)
                    + ". Cette ligne indique quels objets ont été sélectionnés côté écran.";
        }

        if (lower.contains("listbox") && lower.contains("mapping") && lower.contains("nbrselectedobject")) {
            String listbox = extractBracket(msg, "listbox");
            String mapping = extractBracket(msg, "mapping");
            String count = extractBracket(msg, "nbrSelectedObject");

            return "Le système lit le nombre d’éléments sélectionnés dans une liste de l’interface"
                    + valuePart(". Liste", listbox)
                    + valuePart(". Mapping", mapping)
                    + valuePart(". Nombre sélectionné", count)
                    + ". Si la valeur est 0, aucun élément n’a été choisi.";
        }

// ===== CERTIFICAT SET =====
        if (lower.contains("maj_certificatset")) {
            if (lower.contains("start")) {
                return "Début de la mise à jour des certificats associés au dossier. "
                        + "Le système prépare l’ajout ou la synchronisation des certificats sélectionnés.";
            }
            if (lower.contains("end")) {
                return "Fin de la mise à jour des certificats associés au dossier.";
            }
        }

// ===== CONTROLE DONNEES DUM OBLIGATOIRES =====
        if (lower.contains("ctrl_amp_datadumobligatoire")) {
            if (lower.contains("start")) {
                return "Début du contrôle des données DUM obligatoires pour l’AMP. "
                        + "Le système vérifie que la déclaration douanière contient les informations nécessaires.";
            }
            if (lower.contains("end")) {
                return "Fin du contrôle des données DUM obligatoires pour l’AMP.";
            }
        }

        if (lower.contains("dum.get(id)")) {
            String id = extract(msg, "dum\\.get\\(id\\)\\s*\\d*\\s*([^\\s\\|]+)");

            return "Le système lit l’identifiant interne de la DUM"
                    + valuePart(". ID DUM", id)
                    + ". Cette valeur sert à retrouver la déclaration douanière en base.";
        }

        if (lower.contains("dum.getstring(typedeclaration.id)")) {
            String type = extract(msg, "dum\\.getString\\(typeDeclaration\\.id\\)\\s*([^\\s\\|]+)");

            return "Le système lit le type de déclaration DUM"
                    + valuePart(". Type déclaration", type)
                    + ". Cette valeur indique si la DUM est normale, provisionnelle ou d’un autre type.";
        }

// ===== FILL DUM FRET =====
        if (lower.contains("maj_amp_filldumfret")) {
            if (lower.contains("start")) {
                return "Début de l’enrichissement des informations fret de la DUM dans l’AMP. "
                        + "Le système prépare le rattachement des données de fret à la déclaration.";
            }
            if (lower.contains("end")) {
                return "Fin de l’enrichissement des informations fret de la DUM dans l’AMP.";
            }
        }

// ===== CURRENCY =====
        if (lower.contains("currency") && lower.contains("devise_")) {
            String currency = extract(msg, "(DEVISE_[A-Z]+)");

            return "Le système lit la devise utilisée dans le dossier"
                    + valuePart(". Devise", currency)
                    + ". Cette information est utilisée pour les montants liés au fret, à la DUM ou au service.";
        }
        // ===== YM / REFERENCES INTERNES =====
        if (lower.matches(".*-{3,}\\s*ym\\d+\\s*-+.*")) {
            String ym = extract(msg, "(YM\\d+)");
            String values = extract(msg, "YM\\d+\\s*-+\\s*([^\\|]+)");

            return "Le système affiche une référence YM utilisée dans le traitement"
                    + valuePart(". Référence YM", ym)
                    + valuePart(". Valeurs associées", values)
                    + ". Cette ligne sert au suivi interne d’un dossier, service ou rattachement métier.";
        }

        if (lower.contains("tryptique reference")) {
            String ref = extract(msg, "tryptique reference\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit une référence tryptique"
                    + valuePart(". Référence", ref)
                    + ". Cette référence est utilisée pour identifier un document ou dossier lié au transport.";
        }

        if (lower.matches("^\\*{5,}\\d+\\s*$")) {
            String value = extract(msg, "\\*+\\s*(\\d+)");

            return "Le système affiche une valeur de debug interne"
                    + valuePart(". Valeur", value)
                    + ". Cette ligne sert uniquement au suivi technique du traitement.";
        }

// ===== LIBERATION UNITE / BULLETIN =====
        if (lower.contains("maj_liberer_unite_bulletin")) {
            if (lower.contains("start")) {
                return "Début de la libération de l’unité depuis le bulletin. "
                        + "Le système prépare la mise à jour du statut de l’unité afin de la rendre disponible ou de finaliser son passage.";
            }
            if (lower.contains("end")) {
                return "Fin de la libération de l’unité depuis le bulletin. "
                        + "Le statut ou la disponibilité de l’unité a été traité.";
            }
        }

// ===== LIBERATION EQUIPEMENTS DUM =====
        if (lower.contains("maj_liberer_equipements_dums")) {
            if (lower.contains("start")) {
                return "Début de la libération des équipements liés aux DUM. "
                        + "Le système prépare le détachement ou la mise à jour des équipements associés aux déclarations douanières.";
            }

            if (lower.contains("dumscont found")) {
                return "Le système a trouvé des conteneurs DUM associés. "
                        + "Ces éléments seront utilisés pour libérer ou mettre à jour les équipements concernés.";
            }

            if (lower.contains("dum moyen transport found")) {
                return "Le système a trouvé le moyen de transport lié à la DUM. "
                        + "Cette information permet de rattacher la libération de l’équipement au bon véhicule ou transport.";
            }

            if (lower.contains("matricule")) {
                String matricule = extract(msg, "matricule\\s*=\\s*([^\\s\\|]+)");

                return "Le système lit le matricule associé à l’équipement ou au moyen de transport"
                        + valuePart(". Matricule", matricule)
                        + ". Cette valeur permet d’identifier le véhicule ou l’ensemble concerné.";
            }

            if (lower.matches(".*maj_liberer_equipements_dums\\s*-+\\s*[a-z0-9]+\\s*$")) {
                String code = extract(msg, "MAJ_Liberer_Equipements_DUMs\\s*-+\\s*([^\\s\\|]+)");

                return "Le système affiche un code interne pendant la libération des équipements DUM"
                        + valuePart(". Code", code)
                        + ". Ce code sert au suivi du traitement de libération.";
            }

            if (lower.contains("end")) {
                return "Fin de la libération des équipements liés aux DUM.";
            }
        }

// ===== MOTIF / TYPE CONTENANT =====
        if (lower.contains("motif") && lower.contains("*******")) {
            String motif = extract(msg, "motif\\s*\\*+\\s*([^\\s\\|]+)");

            return "Le système lit le motif associé au traitement"
                    + valuePart(". Motif", motif)
                    + ". Une valeur null signifie qu’aucun motif particulier n’a été renseigné.";
        }

        if (lower.contains("typecontenants")) {
            String type = extractBracketAfter(msg, "TypeContenants");

            return "Le système lit le type de contenant"
                    + valuePart(". Type contenant", type)
                    + ". Cette information permet de classifier l’unité ou l’équipement transporté.";
        }

// ===== DUM MOYEN TRANSPORT =====
        if (lower.contains("ws_dum_moyentransport") && lower.contains("json mt")) {
            String count = extract(msg, "JSON MT:\\s*(\\d+)");

            return "Le système lit les moyens de transport transmis dans le JSON DUM"
                    + valuePart(". Nombre d’éléments", count)
                    + ". Cette étape prépare la création ou mise à jour des moyens de transport associés à la déclaration.";
        }

// ===== DML CREE PAR =====
        if (lower.contains("dml_cree_par")) {
            if (lower.contains("start")) {
                return "Début du traitement du champ 'créé par'. "
                        + "Le système prépare l’affectation ou la mise à jour de l’auteur de création de l’objet.";
            }
            if (lower.contains("end")) {
                return "Fin du traitement du champ 'créé par'.";
            }
        }

// ===== DATA LOAD DP / EXISTING COMPONENT =====
        if (lower.contains("maj_data_load_struct_dp")) {
            String status = extractBracketAfter(msg, "MAJ_DATA_LOAD_STRUCT_DP=>");

            return "Le système démarre ou termine la structuration DP pendant le chargement des données"
                    + valuePart(". Statut", status)
                    + ". Cette étape transforme les données paramétrées en objets exploitables.";
        }

        if (lower.contains("maj_data_load_get_existing_principal_cmp")) {
            String status = extractBracketAfter(msg, "MAJ_DATA_LOAD_GET_EXISTING_PRINCIPAL_CMP =>");

            return "Le système recherche le composant principal existant avant insertion ou mise à jour"
                    + valuePart(". Statut", status)
                    + ". Cette étape évite les doublons en vérifiant si l’objet métier existe déjà.";
        }

// ===== POIDS / ARTICLES =====
        if (lower.contains("poidsbruttotal")) {
            String poids = extract(msg, "poidsBrutTotal\\s*([0-9\\.]+)");

            return "Le système lit le poids brut total"
                    + valuePart(". Poids brut", poids)
                    + ". Cette valeur représente le poids total avant déduction du poids net.";
        }

        if (lower.contains("poidsnettotal")) {
            String poids = extract(msg, "poidsNetTotal\\s*([0-9\\.]+)");

            return "Le système lit le poids net total"
                    + valuePart(". Poids net", poids)
                    + ". Cette valeur représente le poids réel de la marchandise.";
        }

        if (lower.contains("nombretotalarticles")) {
            String count = extract(msg, "nombreTotalArticles\\s*(\\d+)");

            return "Le système lit le nombre total d’articles"
                    + valuePart(". Nombre d’articles", count)
                    + ". Cette information décrit le volume d’articles dans la déclaration ou le dossier.";
        }

// ===== PPSOC / TELEPHONE =====
        if (lower.contains("ppsoc")) {
            if (lower.contains("start")) {
                return "Début du traitement PPSoc. "
                        + "Le système vérifie ou prépare les informations de contact liées à la personne ou société.";
            }

            if (lower.contains("tel not null")) {
                String tel = extract(msg, "Tel not NULL\\s*===\\s*([^\\s\\|]+)");

                return "Le système vérifie que le numéro de téléphone est présent"
                        + valuePart(". Téléphone", tel)
                        + ". Cette donnée pourra être utilisée pour contact, notification ou validation.";
            }

            if (lower.contains("indicatif not null")) {
                String indicatif = extract(msg, "Indicatif not NULL\\s*===\\s*([^\\s\\|]+)");

                return "Le système vérifie que l’indicatif téléphonique est présent"
                        + valuePart(". Indicatif", indicatif)
                        + ". Cela permet de formater correctement le numéro de téléphone.";
            }

            if (lower.contains("indicatif pays")) {
                return "Le système traite l’indicatif pays du numéro de téléphone. "
                        + "Cette étape permet d’identifier le pays associé au contact.";
            }

            if (lower.contains("longueur no tel ok")) {
                String length = extract(msg, "Longueur No Tel OK\\s*===\\s*(\\d+)");

                return "Le système valide la longueur du numéro de téléphone"
                        + valuePart(". Longueur", length)
                        + ". Le format du numéro est considéré comme acceptable.";
            }
        }

// ===== STATUT ADMIN / UPDATE SERVICES =====
        if (lower.contains("statutadm before")) {
            String dum = extract(msg, "before\\s*:\\s*([^\\s\\|]+)");
            String statut = extract(msg, "-\\s*(S\\d+)\\|");

            return "Le système lit le statut administratif avant mise à jour"
                    + valuePart(". Référence", dum)
                    + valuePart(". Statut initial", statut)
                    + ". Cette valeur sera comparée au nouveau statut calculé.";
        }

        if (lower.contains("updateadmset_services") && lower.contains("retrieved services")) {
            String count = extract(msg, "dums\\s*:\\s*(\\d+)");

            return "Le système récupère les services/DUM concernés par la mise à jour administrative"
                    + valuePart(". Nombre trouvé", count)
                    + ". Cette étape prépare la modification du statut administratif.";
        }

// ===== BON SORTIE / ETD / CODE SECURITE =====
        if (lower.contains("bonsortiemanuel")) {
            return "Le système traite un bon de sortie manuel. "
                    + "Cette ligne indique qu’une sortie est gérée ou validée manuellement plutôt que par un flux automatique.";
        }

        if (lower.contains("etd")) {
            String etd = extract(msg, "etd_+\\s*:\\s*([^\\s\\|]+)");

            return "Le système lit la date ETD"
                    + valuePart(". ETD", etd)
                    + ". L’ETD correspond généralement à la date estimée de départ.";
        }

        if (lower.contains("codesecurite")) {
            return "Le système traite ou génère un code de sécurité. "
                    + "Ce code peut être utilisé pour sécuriser une opération, une sortie ou une traction.";
        }

// ===== AMP ANNULE =====
        if (lower.contains("maj_insert_amp_annule")) {
            String amp = extract(msg, "MAJ_Insert_AMP_ANNULE\\s*-+\\s*([^\\s\\|]+)");
            String serviceType = extract(msg, "MAJ_Insert_AMP_ANNULE\\s*-+\\s*[^\\s]+\\s*-\\s*([^\\s]+)");
            String matricule = extract(msg, "-\\s*([A-Z0-9]+)\\s*-\\s*(?:null|[A-Z0-9]+)\\s*-\\s*null\\s*-\\s*TYPEUF");

            return "Le système insère une trace d’AMP annulé"
                    + valuePart(". AMP", amp)
                    + valuePart(". Type service", serviceType)
                    + valuePart(". Matricule", matricule)
                    + ". Cette ligne indique qu’un service AMP a été annulé et que les informations de traction/unité sont conservées en traçabilité.";
        }

// ===== STRUCTURE DATA END / COUNT =====
        if (lower.contains("end looping over interface objects")) {
            return "Le système termine la boucle de structuration des objets d’interface. "
                    + "Tous les objets reçus ont été parcourus pour être transformés en données métier.";
        }

        if (lower.contains("nbr of {liststructuredinterfacedatas}")) {
            String count = extractBracketAfter(msg, "nbr of {listStructuredInterfaceDatas}");

            return "Le système indique le nombre de données d’interface structurées"
                    + valuePart(". Nombre", count)
                    + ". Ces objets structurés seront utilisés pour la suite du chargement ou de la sauvegarde.";
        }
        // ===== LIST DOCUMENTS =====
        if (lower.contains("listdocuments")) {
            String count = extract(msg, "(\\d+)$");

            return "Le système récupère la liste des documents associés au dossier"
                    + valuePart(". Nombre de documents", count)
                    + ". Cette étape permet de vérifier quels fichiers ou pièces sont disponibles.";
        }

// ===== ETAT ENREGISTREMENT AMP =====
        if (lower.contains("etatenregistrementamp")) {
            String etat = extract(msg, "(ETAT_[A-Z_]+)");

            return "Le système indique l’état d’enregistrement de l’AMP"
                    + valuePart(". Statut", etat)
                    + ". Cela représente l’état actuel du processus de dématérialisation ou sauvegarde.";
        }

// ===== DUM =====
        if (lower.contains("------------ dum")) {
            String dum = extract(msg, "(\\d{10,})");

            return "Le système traite une déclaration douanière (DUM)"
                    + valuePart(". Numéro DUM", dum)
                    + ". Cette référence identifie le dossier douanier en cours.";
        }

// ===== FOODEX GLOBAL =====
        if (lower.contains("------------ foodex")) {
            String ref = extract(msg, "(\\d{4}/\\d{2}/\\d+)");

            return "Le système traite un dossier FOODEX"
                    + valuePart(". Référence", ref)
                    + ". FOODEX correspond au contrôle sanitaire ou phytosanitaire des marchandises.";
        }

// ===== FOODEX WS INSERT =====
        if (lower.contains("ws_insertfoodex")) {

            String ref = extract(msg, "(\\d{4}/\\d{2}/\\d+)");

            if (lower.contains("article")) {
                return "Début du traitement des articles FOODEX"
                        + valuePart(". Référence", ref)
                        + ". Le système prépare les données des articles soumis au contrôle.";
            }

            if (lower.contains("transportation")) {
                return "Début du traitement du transport FOODEX"
                        + valuePart(". Référence", ref)
                        + ". Le système associe les informations de transport au dossier.";
            }

            if (lower.contains("phytosanitaires")) {
                return "Début du traitement phytosanitaire FOODEX"
                        + valuePart(". Référence", ref)
                        + ". Le système prépare les contrôles sanitaires.";
            }

            if (lower.contains("save")) {
                return "Début de la sauvegarde FOODEX"
                        + valuePart(". Référence", ref)
                        + ". Le système enregistre les données du dossier.";
            }

            return "Traitement FOODEX en cours"
                    + valuePart(". Référence", ref);
        }

// ===== STATUS PDA =====
        if (lower.contains("statuspda")) {
            String status = extract(msg, "statusPDA.*?([A-Za-z]+)");

            return "Le système force le statut PDA"
                    + valuePart(". Statut", status)
                    + ". Cela signifie qu’un état opérationnel est appliqué manuellement ou automatiquement.";
        }

// ===== LIGNES AVEC _____ =====
        if (lower.matches("_{5,}.*")) {
            String value = extract(msg, "(\\d+)");

            return "Le système affiche une valeur technique ou compteur interne"
                    + valuePart(". Valeur", value)
                    + ". Cette ligne sert uniquement au suivi du traitement.";
        }

// ===== TRACABILITES =====
        if (lower.contains("tracabilites")) {
            String count = extract(msg, "(\\d+)");

            return "Le système traite des éléments de traçabilité"
                    + valuePart(". Nombre", count)
                    + ". Ces données permettent de suivre les actions effectuées sur le dossier.";
        }

// ===== DATE DEBUT =====
        if (lower.contains("datedebut")) {
            String date = extract(msg, "([A-Za-z]{3} [A-Za-z]{3} .* \\d{4})");

            return "Le système indique une date de début de traitement"
                    + valuePart(". Date", date)
                    + ". Cette information sert à tracer le démarrage d’une opération.";
        }

// ===== TRACE SIMPLE =====
        if (lower.startsWith("trace")) {
            String trace = extract(msg, "trace\\s*(\\d+)");

            return "Le système affiche un identifiant de trace interne"
                    + valuePart(". Trace ID", trace)
                    + ". Cette valeur permet de suivre une étape spécifique du traitement.";
        }
        // ===== FALLBACK =====
        if (defaultMeaning != null && !defaultMeaning.isBlank()
                && !"Événement détecté dans les logs.".equals(defaultMeaning)) {
            return defaultMeaning;
        }

        if ("ERROR".equalsIgnoreCase(level)) {
            return "Une erreur est présente dans cette ligne. Le message doit être inspecté avec les lignes précédentes pour identifier le déclencheur.";
        }

        return "Message générique de log métier ou technique.";
    }

    private String explainLoadListChilds(String msg, String lower) {
        String duration = extractDuration(msg);
        String thread = extractThread(msg);
        String filter = extractBracket(msg, "filter code");
        String transactionId = extractTransactionId(msg);
        String uuid = extractUuid(msg);
        String size = extract(msg, "size\\s*\\[(\\d+)]");
        String rows = extract(msg, "for\\s*\\[(\\d+)]\\s*row");

        if (lower.contains("query b1 + inserttemp +") || lower.contains("query b2")) {
            return "Le système exécute la deuxième requête de chargement des données enfants après la préparation temporaire"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Taille récupérée", size)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("query b1 + inserttemp")) {
            return "Le système a exécuté la première requête de chargement des enfants puis inséré les résultats en temporaire"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("query b1")) {
            return "Le système exécute la première requête de chargement des données enfants"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Taille récupérée", size)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("start filling data")) {
            return "Le système commence le remplissage des données enfants récupérées"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Nombre de lignes à remplir", rows)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + ".";
        }

        if (lower.contains("filling data")) {
            return "Le système remplit les données enfants récupérées"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        if (lower.contains("total")) {
            return "Le système a terminé le chargement complet des données enfants"
                    + valuePart(" pour le filtre", filter)
                    + valuePart(". Thread", thread)
                    + valuePart(", transactionId", transactionId)
                    + valuePart(", uuid", uuid)
                    + durationPart(duration);
        }

        return "Le système traite le chargement des données enfants"
                + valuePart(" pour le filtre", filter)
                + valuePart(". Thread", thread)
                + valuePart(", transactionId", transactionId)
                + valuePart(", uuid", uuid)
                + durationPart(duration);
    }

    private String explainPipeWorkflowAction(String msg, String prefix) {
        PipeParts p = pipeParts(msg);
        String duration = extractDuration(msg);

        return prefix
                + valuePart(" Process", p.get(1))
                + valuePart(", tâche", p.get(2))
                + valuePart(", action", p.get(3))
                + valuePart(". Identifiant source", p.get(4))
                + valuePart(", identifiant cible", p.get(5))
                + durationPart(duration);
    }

    private String extractUuid(String msg) {
        String uuid = extract(msg, "uuid\\s*\\[([^\\]]+)]");
        if (uuid == null) {
            uuid = extract(msg, "uuid=([^,}\\]]+)");
        }
        return uuid;
    }

    private String buildExplanation(String title, String details, String executionTime) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(".\n\n");
        if (details != null && !details.isBlank()) {
            sb.append(details.trim());
        }
        if (executionTime != null && !executionTime.isBlank()) {
            sb.append("\n\nDurée observée : ").append(executionTime).append(" ms.");
        }
        return sb.toString();
    }

    private String extractExecutionTime(String msg) {
        return extractDuration(msg);
    }

    private long extractExecutionTimeValue(String msg) {
        String duration = extractExecutionTime(msg);
        return parseLong(duration);
    }

    private String extractTransactionId(String msg) {
        String tx = extract(msg, "transactionId\\s*\\[([^\\]]+)]");
        if (tx == null) {
            tx = extract(msg, "transactionId\\s*\\(([^\\)]+)\\)");
        }
        return tx;
    }

    private String extractThread(String msg) {
        String thread = extract(msg, "thread name\\s*\\[([^\\]]+)]");
        if (thread == null) {
            thread = extract(msg, "thread name\\s+([^,\\s]+)");
        }
        return thread;
    }

    private String extractDuration(String msg) {
        String duration = extract(msg, "took\\s*\\[(\\d+)]\\s*ms");
        if (duration == null) {
            duration = extract(msg, "in host took\\s*\\[(\\d+)]\\s*ms");
        }
        if (duration == null) {
            duration = extract(msg, "took\\s*(\\d+)\\s*ms");
        }
        if (duration == null) {
            duration = extract(msg, ";\\s*(\\d+)\\s*\\(ms\\)");
        }
        if (duration == null) {
            duration = extract(msg, "took\\s*(\\d+)\\s*\\(ms\\)");
        }
        if (duration == null) {
            duration = extract(msg, "took\\(ms\\)=(\\d+)");
        }
        return duration;
    }

    private String durationPart(String duration) {
        return duration == null || duration.isBlank()
                ? "."
                : ". Durée observée : " + duration + " ms.";
    }

    private String extractBracket(String msg, String key) {
        return extract(msg, Pattern.quote(key) + "\\s*\\[([^\\]]+)]");
    }

    private String extractBracketAfter(String msg, String marker) {
        if (msg == null || marker == null) return null;

        String sub = msg;
        if (!marker.isBlank()) {
            int idx = msg.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
            if (idx < 0) return null;
            sub = msg.substring(idx);
        }

        Matcher m = Pattern.compile("\\[([^\\]]+)]").matcher(sub);
        if (m.find()) return clean(m.group(1));
        return null;
    }

    private String extractAfter(String msg, String marker) {
        if (msg == null || marker == null) return null;
        int idx = msg.toLowerCase(Locale.ROOT).indexOf(marker.toLowerCase(Locale.ROOT));
        if (idx < 0) return null;

        String sub = msg.substring(idx + marker.length()).trim();
        if (sub.isBlank()) return null;

        int end = sub.indexOf(",");
        if (end < 0) end = sub.indexOf("|");
        if (end < 0) end = sub.indexOf("]");
        if (end > 0) sub = sub.substring(0, end);

        return clean(sub);
    }

    private String extractLastNumber(String msg) {
        if (msg == null) return null;
        Matcher m = Pattern.compile("(\\d{1,9})\\s*$").matcher(msg.trim());
        return m.find() ? m.group(1) : null;
    }

    private String extract(String msg, String regex) {
        if (msg == null || regex == null) return null;
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(msg);
        if (!m.find()) return null;

        if (m.groupCount() >= 2) {
            return clean(m.group(1) + " = " + m.group(2));
        }

        return clean(m.group(1));
    }

    private boolean isDynamicQueryParameter(String lower) {
        return lower.matches(".*\\s+[wf][a-z0-9_]+\\s*=\\[[^\\]]+].*")
                || lower.matches(".*\\s+wv[a-z0-9_]+\\s*=\\[[^\\]]+].*")
                || lower.matches(".*\\s+fv[a-z0-9_]+\\s*=\\[[^\\]]+].*");
    }

    private DynamicParam parseDynamicParam(String msg) {
        String rawKey = extract(msg, "\\s([A-Za-z0-9_]+)\\s*=\\[");
        String value = extract(msg, "=\\[([^\\]]+)]");

        if (rawKey == null) {
            return new DynamicParam("Un paramètre de recherche est présent, mais son nom n’a pas pu être isolé précisément.");
        }

        String key = rawKey;
        String keyLower = rawKey.toLowerCase(Locale.ROOT);

        if (keyLower.startsWith("wv") && keyLower.contains("egal")) {
            String field = key.replaceFirst("(?i)^wv", "").replaceFirst("(?i)egal\\d*$", "");
            return new DynamicParam("Critère d’égalité : le champ \"" + field + "\" doit être égal à \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("wv") && keyLower.contains("like")) {
            String field = key.replaceFirst("(?i)^wv", "").replaceFirst("(?i)like\\d*$", "");
            return new DynamicParam("Critère de recherche partielle : le champ \"" + field + "\" doit ressembler à \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("wv") && keyLower.contains("notlike")) {
            String field = key.replaceFirst("(?i)^wv", "").replaceFirst("(?i)notlike\\d*$", "");
            return new DynamicParam("Critère d’exclusion : le champ \"" + field + "\" ne doit pas correspondre à \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("wv") && keyLower.contains("in")) {
            String field = key.replaceFirst("(?i)^wv", "").replaceFirst("(?i)in\\d*$", "");
            return new DynamicParam("Critère de liste : le champ \"" + field + "\" doit appartenir à la liste \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("fv")) {
            return new DynamicParam("Paramètre technique de classe/type : \"" + key + "\" vaut \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("f")) {
            return new DynamicParam("Paramètre technique de relation ou de champ : \"" + key + "\" vaut \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        if (keyLower.startsWith("w")) {
            return new DynamicParam("Nom de champ métier utilisé dans la requête : \"" + key + "\" vaut \"" + safeValue(value, "valeur non détectée") + "\".");
        }

        return new DynamicParam("Paramètre détecté : \"" + key + "\" = \"" + safeValue(value, "valeur non détectée") + "\".");
    }

    private static class DynamicParam {
        private final String explanation;

        DynamicParam(String explanation) {
            this.explanation = explanation;
        }
    }

    private PipeParts pipeParts(String msg) {
        return new PipeParts(msg);
    }

    private String valuePart(String label, String value) {
        return value == null || value.isBlank() ? "" : " " + label + " \"" + value + "\"";
    }

    private String valuePart(String label, String value, String suffix) {
        return value == null || value.isBlank() ? "" : " " + label + " \"" + value + "\"" + suffix;
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }


    private String extractProcessFilterName(String msg) {
        String filter = extract(msg, "Filtre\\s+([A-Za-z0-9_]+)");
        if (filter == null) {
            filter = extract(msg, "End Filtre\\s+([A-Za-z0-9_]+)");
        }
        return filter;
    }

    private String extractAfterDashes(String msg) {
        if (msg == null) return null;

        Matcher m = Pattern.compile("-{3,}\\s*([^\\-|]+?)\\s*-{3,}").matcher(msg);
        String last = null;
        while (m.find()) {
            last = clean(m.group(1));
        }

        if (last != null && !last.isBlank()) {
            return last;
        }

        return null;
    }

    private String extractProcessValue(String msg, String key) {
        if (msg == null || key == null) return null;

        String regex1 = Pattern.quote(key) + "\\s*::\\s*([^\\-|]+)";
        String value = extract(msg, regex1);

        if (value == null) {
            String regex2 = Pattern.quote(key) + "\\s*-+\\s*([^\\-|]+)";
            value = extract(msg, regex2);
        }

        if (value == null) {
            String regex3 = Pattern.quote(key) + "\\s*:\\s*([^\\|]+)";
            value = extract(msg, regex3);
        }

        return value;
    }

    private String extractStarValue(String msg, String key) {
        if (msg == null || key == null) return null;

        String regex = Pattern.quote(key) + "\\s*\\*+\\s*([^\\|]*)";
        return extract(msg, regex);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private String extractAfterDashesOrTail(String msg) {
        if (msg == null) return null;

        Matcher m = Pattern.compile("-{2,}\\s*([^\\-|\\[]+?)\\s*-{2,}\\s*([^\\|]*)").matcher(msg);
        String last = null;

        while (m.find()) {
            String part1 = clean(m.group(1));
            String part2 = clean(m.group(2));

            if (part2 != null && !part2.isBlank()) {
                last = part2;
            } else if (part1 != null && !part1.isBlank()) {
                last = part1;
            }
        }

        if (last != null && !last.isBlank()) {
            return last;
        }

        String bracket = extract(msg, "\\[([^\\]]+)]");
        if (bracket != null) return bracket;

        String tail = extract(msg, "(\\d{6,})\\s*$");
        if (tail != null) return tail;

        return null;
    }

    private String extractProcessDurationEquals(String msg) {
        return extract(msg, "took\\(ms\\)=(\\d+)");
    }

    private String extractMapField(String msg, String key) {
        if (msg == null || key == null) return null;
        return extract(msg, Pattern.quote(key) + "=([^,}\\|]+)");
    }

    private String extractIgnoredServiceName(String msg) {
        String service = extract(msg, "Start\\s+(WS_[A-Za-z0-9_]+)\\s+IGNORED");
        if (service == null) {
            service = extract(msg, "Start\\s+(WS_[A-Za-z0-9_]+)");
        }
        return service;
    }



    private String extractFilterCode(String msg) {
        String v = extract(msg, "filter code\\s*\\[([^\\]]+)]");
        if (v == null) v = extract(msg, "fetching filter\\s*\\[([^\\]]+)]");
        return v;
    }

    private String extractClassName(String msg) {
        String v = extract(msg, "className\\s*\\[([^\\]]+)]");
        if (v == null) v = extract(msg, "className\\s+([^,\\|]+)");
        return v;
    }

    private String extractRows(String msg) {
        String v = extract(msg, "\\[(\\d+)]\\s*row\\s*fetched");
        if (v == null) v = extract(msg, "\\|\\s*(\\d+)\\s*row\\s*\\|");
        if (v == null) v = extract(msg, "Query Select\\]\\s*took\\s*\\[?\\d+]?\\s*ms\\|\\s*(\\d+)\\s*row");
        return v;
    }

    private String extractNoService(String msg) {
        String v = extract(msg, "wvnoServiceegal\\d*\\s*=\\s*\\[([^\\]]+)]");
        if (v == null) v = extract(msg, "det_comp_fils\\d*\\.string_value\\s*=\\s*([^\\s\\|]+)");
        if (v == null) v = extract(msg, "Start Find service\\s*\\*+\\s*([^\\s\\|]+)");
        if (v == null) v = extract(msg, "noService\\s*[:=]\\s*([^\\s\\|]+)");
        return v;
    }

    private String extractValueForKey(String msg, String key) {
        if (msg == null || key == null) return null;

        String q = Pattern.quote(key);

        String v = extract(msg, "wv" + q + "[A-Za-z]*\\d*\\s*=\\s*\\[([^\\]]+)]");
        if (v == null) v = extract(msg, "w" + q + "\\d*\\s*=\\s*\\[([^\\]]+)]");
        if (v == null) v = extract(msg, q + "\\s*::\\s*([^\\|\\-]+)");
        if (v == null) v = extract(msg, q + "\\s*-+\\s*([^\\|\\-]+)");
        if (v == null) v = extract(msg, q + "\\s*[:=]\\s*([^\\s\\|,]+)");

        if (v == null && "amp".equalsIgnoreCase(key)) {
            v = extract(msg, "wvampegal\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "ampe".equalsIgnoreCase(key)) {
            v = extract(msg, "wvampeegal\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "noUnite".equalsIgnoreCase(key)) {
            v = extract(msg, "wvnoUniteegal\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "nomService".equalsIgnoreCase(key)) {
            v = extract(msg, "wvnomServicein\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "dateAction".equalsIgnoreCase(key)) {
            v = extract(msg, "wvdateActionsupEgald\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "dateCreation".equalsIgnoreCase(key)) {
            v = extract(msg, "wvdateCreationsupEgald\\d*\\s*=\\s*\\[([^\\]]+)]");
        }
        if (v == null && "dateMessage".equalsIgnoreCase(key)) {
            v = extract(msg, "wvdateMessagesupEgald\\d*\\s*=\\s*\\[([^\\]]+)]");
        }

        return v;
    }

    private String extractCheckpointValue(String msg) {
        String v = extract(msg, "trCheckPoint\\s*:\\s*([^\\|]+)");
        if (v == null) v = extract(msg, "trCheckPoint\\s*-+\\s*([^\\|]+)");
        if (v == null) v = extract(msg, "(ENTREE_[A-Z_]+|SORTIE_[A-Z_]+)");
        if (v == null) v = extract(msg, "(ENTREE\\s+[A-Z ]+|SORTIE\\s+[A-Z ]+)");
        return v;
    }

    private String explainCheckpointWorks(String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank()) {
            return "Le checkpoint n’a pas pu être identifié.";
        }

        String cp = checkpoint.toUpperCase(Locale.ROOT).trim();

        if (cp.contains("ENTREE_COULOIR_RFID")) {
            return "Entrée dans le couloir RFID : passage automatiquement détecté par lecteur RFID.";
        }
        if (cp.contains("ENTREE_PARK_VISITE") || cp.contains("ENTREE PARK VISITE")) {
            return "Entrée dans le parc de visite : l’objet arrive dans une zone de contrôle ou d’inspection.";
        }
        if (cp.contains("ENTREE_SCANNER_EXPORT")) {
            return "Entrée scanner export : l’objet passe dans une étape de contrôle scanner avant export.";
        }
        if (cp.contains("SORTIE_SAS_EXPORT")) {
            return "Sortie du SAS export : l’objet quitte une zone de transition export.";
        }
        if (cp.contains("ENTREE_TERMINAL_EXPORT")) {
            return "Entrée terminal export : l’objet arrive dans la zone terminal liée au flux export.";
        }
        if (cp.contains("SORTIE SAS IMPORT") || cp.contains("SORTIE_SAS_IMPORT")) {
            return "Sortie du SAS import : l’objet quitte une zone de transition import.";
        }
        if (cp.contains("ENTREE SAS IMPORT") || cp.contains("ENTREE_SAS_IMPORT")) {
            return "Entrée dans le SAS import : l’objet entre dans une zone de transition import.";
        }

        return "Checkpoint métier détecté : il représente une étape du parcours logistique.";
    }

    private String explainCheckpoint(String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank()) {
            return "Le checkpoint n’a pas pu être identifié.";
        }

        String cp = checkpoint.toUpperCase(Locale.ROOT).trim();

        if (cp.contains("ENTREE_COULOIR_RFID")) {
            return "Entrée dans un couloir RFID : passage automatiquement détecté par lecteur RFID.";
        }
        if (cp.contains("ENTREE_PARK_VISITE")) {
            return "Entrée dans le parc de visite : l’objet passe dans une zone de contrôle ou d’inspection.";
        }
        if (cp.contains("ENTREE_SCANNER_EXPORT")) {
            return "Entrée au scanner export : l’objet passe par une étape de contrôle scanner.";
        }
        if (cp.contains("SORTIE_SAS_EXPORT")) {
            return "Sortie du SAS export : l’objet quitte une zone de transition export.";
        }
        if (cp.contains("ENTREE_TERMINAL_EXPORT")) {
            return "Entrée au terminal export : l’objet arrive dans la zone terminal.";
        }
        if (cp.contains("SAS IMPORT")) {
            return "Étape SAS import : l’objet passe par une zone de transition import.";
        }

        return "Checkpoint métier détecté : il représente une étape du parcours logistique.";
    }

    private static class PipeParts {
        private final String[] parts;

        PipeParts(String msg) {
            this.parts = msg == null ? new String[0] : msg.split("\\|", -1);
        }

        String get(int index) {
            if (index < 0 || index >= parts.length) return null;
            String value = parts[index];
            if (value == null) return null;
            value = value.trim();
            return value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
        }
    }
}
