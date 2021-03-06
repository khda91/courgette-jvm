package courgette.runtime.report.builder;

import courgette.runtime.CourgetteProperties;
import courgette.runtime.CourgetteRunResult;
import courgette.runtime.report.model.Embedding;
import courgette.runtime.report.model.Feature;
import courgette.runtime.report.model.Hook;
import courgette.runtime.report.model.Result;
import courgette.runtime.report.model.Scenario;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HtmlReportBuilder {
    private List<Feature> featureList;
    private List<CourgetteRunResult> courgetteRunResults;
    private CourgetteProperties courgetteProperties;

    private static final String PASSED = "Passed";
    private static final String PASSED_AFTER_RERUN = "Passed after Rerun";
    private static final String FAILED = "Failed";
    private static final String FAILED_AFTER_RERUN = "Failed after Rerun";
    private static final String SUCCESS = "success";
    private static final String DANGER = "danger";
    private static final String WARNING = "warning";

    private HtmlReportBuilder(List<Feature> featureList,
                              List<CourgetteRunResult> courgetteRunResults,
                              CourgetteProperties courgetteProperties) {

        this.featureList = featureList;
        this.courgetteRunResults = courgetteRunResults;
        this.courgetteProperties = courgetteProperties;
    }

    public static HtmlReportBuilder create(List<Feature> featureList,
                                           List<CourgetteRunResult> courgetteRunResults,
                                           CourgetteProperties courgetteProperties) {

        return new HtmlReportBuilder(featureList, courgetteRunResults, courgetteProperties);
    }

    public String getHtmlTableFeatureRows() {
        final StringBuilder tableRows = new StringBuilder();
        featureList.forEach(feature -> tableRows.append(TableRowBuilder.create(feature, courgetteRunResults).getFeatureRow()));
        return tableRows.toString();
    }

    public String getHtmlModals() {
        final StringBuilder modals = new StringBuilder();

        featureList
                .forEach(feature -> {
                    List<Scenario> scenarios = feature.getScenarios();
                    scenarios.forEach(scenario -> modals.append(ModalBuilder.create(feature, scenario).getModal()));
                });

        modals.append(ModalBuilder.create(getEnvironmentInfo()).getEnvironmentInfoModal());

        return modals.toString();
    }

    private static Function<Result, String> statusLabel = (result) -> result.getStatus().substring(0, 1).toUpperCase() + result.getStatus().substring(1);

    private static Function<Result, String> statusBadge = (result) -> {
        String status = result.getStatus();
        return status.equalsIgnoreCase(PASSED) ? SUCCESS : status.equalsIgnoreCase(FAILED) ? DANGER : WARNING;
    };

    static class TableRowBuilder {
        private Feature feature;
        private List<CourgetteRunResult> courgetteRunResults;
        private boolean hasReruns;

        private TableRowBuilder(Feature feature, List<CourgetteRunResult> courgetteRunResults) {
            this.feature = feature;
            this.courgetteRunResults = courgetteRunResults;
            this.hasReruns = this.courgetteRunResults.stream().anyMatch(result -> result.getStatus() == CourgetteRunResult.Status.RERUN);
        }

        public static TableRowBuilder create(Feature feature, List<CourgetteRunResult> courgetteRunResults) {
            return new TableRowBuilder(feature, courgetteRunResults);
        }

        private final String FEATURE_ROW_START = "<tr>\n" +
                "                                   <td>\n" +
                "                                        <a href=\"\" data-toggle=\"collapse\" data-target=\"#%s\">%s</a>\n" +
                "                                        <div class=\"collapse mt-2\" id=\"%s\">\n";

        private final String FEATURE_ROW_DETAIL = "          <hr>\n" +
                "                                            <div class=\"row\">\n" +
                "                                                <a href=\"\" data-toggle=\"modal\" data-target=\"#%s\" id=\"child-row\" class=\"col-lg-9\">%s\n" +
                "                                                </a>\n" +
                "                                                <div class=\"col-lg-2\">\n" +
                "                                                    <span class=\"float-right badge badge-%s\">%s</span>\n" +
                "                                                </div>\n" +
                "                                            </div>\n";

        private final String FEATURE_ROW_END = "</td>\n" +
                "                                    <td>\n" +
                "                                        <span class=\"float-left badge badge-%s\">%s</span>\n" +
                "                                    </td>\n" +
                "                                </tr>\n";

        private String getFeatureRow() {
            final StringBuilder featureRow = new StringBuilder();

            String featureId = feature.getCourgetteFeatureId();
            String featureName = feature.getName();
            String featureBadge = feature.passed() ? SUCCESS : DANGER;
            String featureStatus = featureBadge.equals(SUCCESS) ? PASSED : FAILED;

            featureRow.append(String.format(FEATURE_ROW_START, featureId, featureName, featureId));
            getScenario(featureRow, FEATURE_ROW_DETAIL);
            featureRow.append(String.format(FEATURE_ROW_END, featureBadge, featureStatus));

            return featureRow.toString();
        }

        private void getScenario(StringBuilder source, String format) {
            feature.getScenarios().forEach(scenario -> {
                if (!scenario.getKeyword().equalsIgnoreCase("Background")) {
                    String scenarioId = scenario.getCourgetteScenarioId();
                    String scenarioName = scenario.getName();
                    String scenarioBadge = scenario.passed() ? SUCCESS : DANGER;
                    String scenarioStatus = scenarioBadge.equals(SUCCESS) ? PASSED : FAILED;

                    switch (scenarioBadge) {
                        case DANGER:
                            if (hasReruns) {
                                scenarioStatus = FAILED_AFTER_RERUN;
                            }
                            break;

                        case SUCCESS:
                            if (hasReruns) {
                                List<CourgetteRunResult> scenarioRunResults = courgetteRunResults.stream().filter(result -> result.getFeatureUri().equalsIgnoreCase(scenario.getFeatureUri() + ":" + scenario.getLine())).collect(Collectors.toList());

                                if (scenarioRunResults.stream().anyMatch(result -> result.getStatus() == CourgetteRunResult.Status.PASSED_AFTER_RERUN)) {
                                    scenarioStatus = PASSED_AFTER_RERUN;
                                }
                            }
                            break;
                    }

                    source.append(String.format(format, scenarioId, scenarioName, scenarioBadge, scenarioStatus));
                }
            });
        }
    }

    static class ModalBuilder {
        private Feature feature;
        private Scenario scenario;
        private List<String> environmentInfo;

        private ModalBuilder(List<String> environmentInfo) {
            this.environmentInfo = environmentInfo;
        }

        private ModalBuilder(Feature feature, Scenario scenario) {
            this.feature = feature;
            this.scenario = scenario;
        }

        public static ModalBuilder create(List<String> environmentInfo) {
            return new ModalBuilder(environmentInfo);
        }

        public static ModalBuilder create(Feature feature, Scenario scenario) {
            return new ModalBuilder(feature, scenario);
        }

        private final String MODEL_HEADER =
                "<div class=\"modal fade\" id=\"%s\" tabindex=\"-1\" role=\"dialog\" aria-labelledby=\"%s\" aria-hidden=\"true\">\n" +
                        "<div class=\"modal-dialog modal-lg\" role=\"document\">\n" +
                        "<div class=\"modal-content\">\n" +
                        "<div class=\"modal-header text-white bg-dark\">\n" +
                        "<span class=\"modal-title\"><h5>%s</h5>\n" +
                        "<div class=\"font-italic text-muted\">%s - line %s</div></span>\n" +
                        "<button type=\"button\" class=\"close text-white\" data-dismiss=\"modal\" aria-label=\"Close\">\n" +
                        "<span aria-hidden=\"true\">&times;</span>\n" +
                        "</button>\n" +
                        "</div>\n";

        private final String MODEL_ENVIRONMENT_INFO_HEADER =
                "<div class=\"modal fade\" id=\"environment_info\" tabindex=\"-1\" role=\"dialog\" aria-labelledby=\"environment_info\" aria-hidden=\"true\">\n" +
                        "<div class=\"modal-dialog modal-lg\" role=\"document\">\n" +
                        "<div class=\"modal-content\">\n" +
                        "<div class=\"modal-header text-white bg-dark\">\n" +
                        "<span class=\"modal-title\"><h5>Environment Information</h5></span>\n" +
                        "<button type=\"button\" class=\"close text-white\" data-dismiss=\"modal\" aria-label=\"Close\">\n" +
                        "<span aria-hidden=\"true\">&times;</span>\n" +
                        "</button>\n" +
                        "</div>\n";

        private final String MODEL_BODY_ROW =
                "<div class=\"row\">\n" +
                        "<div class=\"col-lg-9\" style=\"overflow-wrap:break-word;\">\n" +
                        "%s\n";

        private final String MODEL_BODY_ROW_RESULT =
                "<div class=\"col-lg-3\">\n" +
                        "<span class=\"float-right\">\n" +
                        "<span class=\"badge badge-info\">%s ms</span>\n" +
                        "<span class=\"badge badge-%s\">%s</span>\n" +
                        "</span>\n" +
                        "</div>\n" +
                        "</div>\n";

        private final String MODAL_BODY_ROW_BASE64IMAGE_EMBEDDINGS =
                "<div class=\"row mt-2\">\n" +
                        "<div class=\"col-lg-12\">\n" +
                        "<img src=\"data:image/%s;base64,%s\" class=\"img-thumbnail\">\n" +
                        "</div>\n" +
                        "</div>\n";

        private final String MODAL_BODY_ROW_TEXT_HTML_EMBEDDINGS =
                "<div class=\"row mt-2\">\n" +
                        "<div class=\"col-lg-12\" style=\"overflow-wrap:break-word;\">\n" +
                        "%s\n" +
                        "</div>\n" +
                        "</div>\n";

        private final String MODAL_BODY_ROW_ERROR_MESSAGE =
                "<div class=\"row mt-2\">\n" +
                        "<div class=\"col-lg-12 text-danger\" style=\"overflow-wrap:break-word;\">\n" +
                        "%s\n" +
                        "</div>\n" +
                        "</div>\n";

        private final String MODAL_BODY_ROW_OUTPUT =
                "<div class=\"row mt-2\">\n" +
                        "<div class=\"col-lg-12 text-info\" style=\"overflow-wrap:break-word;\">\n" +
                        "%s\n" +
                        "</div>\n" +
                        "</div>\n";

        private final String MODAL_BODY_ROW_DATATABLE =
                "<div class=\"row mt-2\">\n" +
                        "<div class=\"col-lg-12 text-muted\" style=\"overflow-wrap:break-word;\">\n" +
                        "%s\n" +
                        "</div>\n" +
                        "</div>";

        private Function<Hook, String> hookFunc = (hook -> {
            StringBuilder hookBuilder = new StringBuilder();

            String name = hook.getLocation();
            Result result = hook.getResult();

            hookBuilder.append(String.format(MODEL_BODY_ROW, name));
            hookBuilder.append("</div>\n");
            hookBuilder.append(String.format(MODEL_BODY_ROW_RESULT, result.getDuration(), statusBadge.apply(result), statusLabel.apply(result)));

            if (result.getErrorMessage() != null) {
                hookBuilder.append(String.format(MODAL_BODY_ROW_ERROR_MESSAGE, result.getErrorMessage()));
            }

            hook.getOutput().forEach(output -> hookBuilder.append(String.format(MODAL_BODY_ROW_OUTPUT, output)));

            addEmbeddings(hookBuilder, hook.getEmbeddings());

            hookBuilder.append("<hr>\n");
            return hookBuilder.toString();
        });

        private void addEmbeddings(StringBuilder hookBuilder, List<Embedding> embeddings) {
            embeddings.forEach(embedding -> {
                if (embedding.getMimeType().equals("text/html")) {
                    String htmlData = new String(Base64.getDecoder().decode(embedding.getData()));
                    hookBuilder.append(String.format(MODAL_BODY_ROW_TEXT_HTML_EMBEDDINGS, htmlData));
                } else if (embedding.getMimeType().startsWith("image")) {
                    final String imageFormat = embedding.getMimeType().split("/")[1];
                    hookBuilder.append(String.format(MODAL_BODY_ROW_BASE64IMAGE_EMBEDDINGS, imageFormat, embedding.getData()));
                }
            });
        }

        private String getEnvironmentInfoModal() {
            final StringBuilder modal = new StringBuilder();
            modal.append(MODEL_ENVIRONMENT_INFO_HEADER);

            modal.append("<div class=\"modal-body\">\n");

            environmentInfo.forEach(info -> {
                modal.append(String.format(MODEL_BODY_ROW, info));
                modal.append("</div>\n");
                modal.append("</div>\n");
                modal.append("<hr>\n");
            });

            modal.append("</div>\n" +
                    "</div>\n" +
                    "</div>\n\n");

            return modal.toString();
        }

        private String getModal() {
            final StringBuilder modal = new StringBuilder();

            final String featureName = feature.getUri().substring(feature.getUri().lastIndexOf("/") + 1);

            modal.append(String.format(MODEL_HEADER, scenario.getCourgetteScenarioId(), scenario.getCourgetteScenarioId(), scenario.getName(), featureName, scenario.getLine()));

            modal.append("<div class=\"modal-body\">\n");

            scenario.getBefore().forEach(before -> modal.append(hookFunc.apply(before)));

            scenario.getSteps().forEach(step -> {
                step.getBefore().forEach(beforeStep -> modal.append(hookFunc.apply(beforeStep)));

                String stepKeyword = step.getKeyword();
                String stepName = step.getName();
                long stepDuration = step.getResult().getDuration();
                String stepStatus = statusLabel.apply(step.getResult());
                String stepStatusBadge = statusBadge.apply(step.getResult());

                modal.append(String.format(MODEL_BODY_ROW, stepKeyword + stepName));
                modal.append("</div>\n");

                modal.append(String.format(MODEL_BODY_ROW_RESULT, stepDuration, stepStatusBadge, stepStatus));

                step.getRowData().forEach(row -> modal.append(String.format(MODAL_BODY_ROW_DATATABLE, row)));

                if (step.getResult().getErrorMessage() != null) {
                    modal.append(String.format(MODAL_BODY_ROW_ERROR_MESSAGE, step.getResult().getErrorMessage()));
                }

                step.getOutput().forEach(output -> modal.append(String.format(MODAL_BODY_ROW_OUTPUT, output)));

                addEmbeddings(modal, step.getEmbeddings());

                modal.append("<hr>\n");

                step.getAfter().forEach(afterStep -> modal.append(hookFunc.apply(afterStep)));
            });

            scenario.getAfter().forEach(after -> modal.append(hookFunc.apply(after)));

            modal.append("</div>\n" +
                    "</div>\n" +
                    "</div>\n" +
                    "</div>\n\n");

            return modal.toString();
        }
    }

    private List<String> getEnvironmentInfo() {
        final List<String> envData = new ArrayList<>();

        final String envInfo = courgetteProperties.getCourgetteOptions().environmentInfo().trim();

        final String[] values = envInfo.split(";");

        for (String value : values) {
            String[] keyValue = value.trim().split("=");

            if (keyValue.length == 2) {
                envData.add(keyValue[0].trim() + " = " + keyValue[1].trim());
            }
        }

        if (envData.isEmpty()) {
            envData.add("No additional environment information provided.");
        }

        return envData;
    }
}
