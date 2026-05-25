package yhx.com.domain.agent.model.valobj.enums.memory;

public enum VectorCollectionTypeEnumVO {

    TURN_SUMMARY("vec_turn_summary"),
    CONVERSATION_SUMMARY("vec_conversation_summary"),
    LONG_TERM_MEMORY("vec_long_term_memory"),
    USER_PREFERENCE("vec_user_preference"),
    ARTIFACT_SUMMARY("vec_artifact_summary"),
    ARTIFACT_CHUNK("vec_artifact_chunk"),
    RAG_DOCUMENT("vec_rag_document"),
    RAG_CHUNK("vec_rag_chunk");

    private final String collectionName;

    VectorCollectionTypeEnumVO(String collectionName) {
        this.collectionName = collectionName;
    }

    public String collectionName() {
        return collectionName;
    }
}
