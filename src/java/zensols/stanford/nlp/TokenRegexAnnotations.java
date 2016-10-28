package zensols.stanford.nlp;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.TypesafeMap;

public final class TokenRegexAnnotations {
    private TokenRegexAnnotations() {}

    public final static class NERAnnotation implements CoreAnnotation<String> {
	private NERAnnotation() {}

	public Class<String> getType() {
	    return String.class;
	}
    }

    public final static class NERNormalizedAnnotation implements CoreAnnotation<String> {
	private NERNormalizedAnnotation() {}

	public Class<String> getType() {
	    return String.class;
	}
    }

    public final static class NERFeatureCreateAnnotation implements CoreAnnotation<String> {
	private NERFeatureCreateAnnotation() {}

	public Class<String> getType() {
	    return String.class;
	}
    }

    public final static class NERItemIDAnnotation implements CoreAnnotation<String> {
	private NERItemIDAnnotation() {}

	public Class<String> getType() {
	    return String.class;
	}
    }

    public static class MentionsAnnotation implements CoreAnnotation<List<CoreMap>> {
	public Class<List<CoreMap>> getType() {
	    return ErasureUtils.uncheckedCast(List.class);
	}
    }
}
