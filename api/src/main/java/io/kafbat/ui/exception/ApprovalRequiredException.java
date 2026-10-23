package io.kafbat.ui.exception;

public class ApprovalRequiredException extends CustomBaseException {

  private final String approvalId;

  public ApprovalRequiredException(String message, String approvalId) {
    super(message);
    this.approvalId = approvalId;
  }

  public String getApprovalId() {
    return approvalId;
  }

  @Override
  public ErrorCode getErrorCode() {
    return ErrorCode.APPROVAL_REQUIRED;
  }
}
