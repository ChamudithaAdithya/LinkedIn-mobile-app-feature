import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/candidate.dart';
import '../models/search_request.dart';
import '../models/select_request.dart';
import '../models/select_response.dart';
import '../services/leadfinder_api.dart';

final searchProvider = StateNotifierProvider<SearchNotifier, SearchState>(
  (ref) => SearchNotifier(LeadfinderApi()),
);

class SearchState {
  final bool isLoading;
  final List<Candidate> results;
  final Candidate? selectedCandidate;
  final String? selectedMessage;
  final String? errorMessage;
  final String? searchedCompany;

  SearchState({
    this.isLoading = false,
    this.results = const [],
    this.selectedCandidate,
    this.selectedMessage,
    this.errorMessage,
    this.searchedCompany,
  });

  SearchState copyWith({
    bool? isLoading,
    List<Candidate>? results,
    Candidate? selectedCandidate,
    String? selectedMessage,
    String? errorMessage,
    String? searchedCompany,
  }) {
    return SearchState(
      isLoading: isLoading ?? this.isLoading,
      results: results ?? this.results,
      selectedCandidate: selectedCandidate ?? this.selectedCandidate,
      selectedMessage: selectedMessage ?? this.selectedMessage,
      errorMessage: errorMessage,
      searchedCompany: searchedCompany ?? this.searchedCompany,
    );
  }
}

class SearchNotifier extends StateNotifier<SearchState> {
  final LeadfinderApi api;

  SearchNotifier(this.api) : super(SearchState());

  Future<void> search(String name, String? company, String? title) async {
    state = state.copyWith(
      isLoading: true,
      errorMessage: null,
      searchedCompany: company,
    );
    try {
      final results = await api.searchCandidates(
        SearchRequest(name: name, company: company, title: title),
      );
      state = state.copyWith(isLoading: false, results: results);
    } catch (error) {
      final message = error is DioException
          ? 'Search failed: ${error.type} ${error.message} ${error.requestOptions.uri}'
          : 'Search failed: ${error.toString()}';
      state = state.copyWith(isLoading: false, errorMessage: message);
    }
  }

  Future<void> selectCandidate(Candidate candidate) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    Candidate payloadCandidate = candidate;
    if ((candidate.company == null || candidate.company!.isEmpty) &&
        state.searchedCompany != null &&
        state.searchedCompany!.isNotEmpty) {
      payloadCandidate = candidate.copyWith(company: state.searchedCompany);
    }

    try {
      final selectResp = await api.selectCandidate(
        SelectRequest(
          selectedCandidate: payloadCandidate,
        ), // Use the patched candidate
      );

      final message = selectResp.message ?? '';
      final lower = message.toLowerCase();
      if (lower.contains('did not') ||
          lower.contains('failed') ||
          lower.contains('insufficient') ||
          lower.contains('no data')) {
        state = state.copyWith(isLoading: false, errorMessage: message);
      } else {
        state = state.copyWith(
          isLoading: false,
          selectedCandidate: selectResp.candidate,
          selectedMessage: selectResp.message,
        );
      }
    } catch (error) {
      final message = error is DioException
          ? 'Selection failed: ${error.type} ${error.message} ${error.requestOptions.uri}'
          : 'Selection failed: ${error.toString()}';
      state = state.copyWith(isLoading: false, errorMessage: message);
    }
  }
}
