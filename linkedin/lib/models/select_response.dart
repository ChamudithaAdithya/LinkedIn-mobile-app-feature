import 'candidate.dart';

class SelectResponse {
  final Candidate candidate;
  final String message;

  SelectResponse({required this.candidate, required this.message});

  factory SelectResponse.fromJson(Map<String, dynamic> json) {
    return SelectResponse(
      candidate: Candidate.fromJson(json['candidate'] as Map<String, dynamic>),
      message: json['message'] as String? ?? '',
    );
  }
}
