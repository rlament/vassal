/*
 *
 * Copyright (c) 2009-2021 The VASSAL Development Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.script.expression;

public class ExpressionException extends AuditableException {
  private static final long serialVersionUID = 1L;

  protected String expression;
  protected String error;

  public ExpressionException(String s) {
    this(s, "");
  }

  public ExpressionException(String s, String e) {
    this(s, e, null, null, null);
  }

  public ExpressionException(String s, String e, Auditable owner, AuditTrail auditTrail) {
    this(s, e, owner, auditTrail, null);
  }

  public ExpressionException(String s, String e, Auditable owner, AuditTrail auditTrail, Throwable cause) {
    super(owner, auditTrail, cause);
    expression = s;
    error = e;
  }

  public String getExpression() {
    return expression;
  }

  public String getError() {
    return error;
  }

}
