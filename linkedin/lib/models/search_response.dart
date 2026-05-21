import 'candidate.dart';

class SearchResponse {
  final List<Candidate> candidates;

  SearchResponse({required this.candidates});

  factory SearchResponse.fromJson(Map<String, dynamic> json) {
    final items = json['candidates'] as List<dynamic>? ?? [];
    final candidates = items
        .map((item) => Candidate.fromJson(item as Map<String, dynamic>))
        .toList();
    return SearchResponse(candidates: candidates);
  }
}
