package com.programmers.film.domain.common.domain;

import java.util.Objects;
import javax.persistence.Embeddable;
import lombok.Getter;

@Getter
@Embeddable
public class ImageUrl {
    private String originalSizeUrl;
    private String smallSizeUrl;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ImageUrl imageUrl = (ImageUrl) o;
        return Objects.equals(getOriginalSizeUrl(), imageUrl.getOriginalSizeUrl())
            && Objects.equals(getSmallSizeUrl(), imageUrl.getSmallSizeUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOriginalSizeUrl(), getSmallSizeUrl());
    }
}
