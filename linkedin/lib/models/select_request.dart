import 'candidate.dart';

class SelectRequest {
  final Candidate selectedCandidate;

  SelectRequest({required this.selectedCandidate});

  Map<String, dynamic> toJson() {
    return {'selectedCandidate': selectedCandidate.toJson()};
  }
}
