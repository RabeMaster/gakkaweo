package com.gakkaweo.backend.admin.sort;

import jakarta.persistence.criteria.Path;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaOrder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public final class SortSpecBuilder {

  private SortSpecBuilder() {}

  public static <T> Specification<T> build(SortRequestParser.SortSpec spec, String tiebreakField) {
    return (root, query, cb) -> {
      if (Long.class != query.getResultType()) {
        HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
        Path<?> primaryPath = root.get(spec.entityField());
        JpaOrder primary =
            (spec.direction() == Sort.Direction.ASC ? hcb.asc(primaryPath) : hcb.desc(primaryPath))
                .nullPrecedence(NullPrecedence.LAST);
        query.orderBy(primary, cb.desc(root.get(tiebreakField)));
      }
      return cb.conjunction();
    };
  }
}
