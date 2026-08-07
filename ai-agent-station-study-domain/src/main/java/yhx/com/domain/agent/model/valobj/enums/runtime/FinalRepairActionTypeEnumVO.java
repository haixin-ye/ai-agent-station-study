package yhx.com.domain.agent.model.valobj.enums.runtime;

public enum FinalRepairActionTypeEnumVO {
    REPAIR_FINAL("REPAIR_FINAL");

    private final String code;

    FinalRepairActionTypeEnumVO(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
