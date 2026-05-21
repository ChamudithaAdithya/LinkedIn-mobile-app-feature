import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/search_provider.dart';

class ResultsScreen extends ConsumerStatefulWidget {
  const ResultsScreen({super.key});

  @override
  ConsumerState<ResultsScreen> createState() => _ResultsScreenState();
}

class _ResultsScreenState extends ConsumerState<ResultsScreen> {
  @override
  Widget build(BuildContext context) {
    final state = ref.watch(searchProvider);
    print(state.results);
    return Scaffold(
      appBar: AppBar(title: const Text('Results')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: state.isLoading
            ? const Center(child: CircularProgressIndicator())
            : state.errorMessage != null
            ? Center(child: Text(state.errorMessage!))
            : state.results.isEmpty
            ? const Center(child: Text('No candidates found.'))
            : Column(
                children: [
                  Expanded(
                    child: ListView.builder(
                      itemCount: state.results.length,
                      itemBuilder: (context, index) {
                        final candidate = state.results[index];
                        return Card(
                          margin: const EdgeInsets.only(bottom: 12),
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Row(
                              children: [
                                CircleAvatar(
                                  radius: 28,
                                  backgroundImage:
                                      candidate.profilePicUrl != null
                                      ? NetworkImage(candidate.profilePicUrl!)
                                      : null,
                                  child: candidate.profilePicUrl == null
                                      ? const Icon(Icons.person, size: 32)
                                      : null,
                                ),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        candidate.name,
                                        style: const TextStyle(
                                          fontWeight: FontWeight.bold,
                                          fontSize: 16,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        candidate.headline ??
                                            '${candidate.title}${candidate.company != null ? ' · ${candidate.company}' : ''}',
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                          color: Colors.grey[600],
                                          fontSize: 14,
                                        ),
                                      ),
                                      if (candidate.location != null) ...[
                                        const SizedBox(height: 4),
                                        Text(
                                          candidate.location!,
                                          style: TextStyle(
                                            color: Colors.grey[500],
                                            fontSize: 12,
                                          ),
                                        ),
                                      ],
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text(
                                      '${candidate.confidence}%',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: Colors.blue[700],
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    const SizedBox(height: 8),
                                    ElevatedButton(
                                      style: ElevatedButton.styleFrom(
                                        visualDensity: VisualDensity.compact,
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 12,
                                          vertical: 8,
                                        ),
                                      ),
                                      onPressed: () async {
                                        // 1. Capture BOTH the router and the messenger BEFORE the await
                                        final router = GoRouter.of(context);
                                        final messenger = ScaffoldMessenger.of(
                                          context,
                                        );

                                        // 2. Make the API call
                                        await ref
                                            .read(searchProvider.notifier)
                                            .selectCandidate(candidate);

                                        final newState = ref.read(
                                          searchProvider,
                                        );

                                        // 3. Check if the screen is still active
                                        if (!mounted) return;

                                        // 4. Use the captured variables!
                                        if (newState.errorMessage == null) {
                                          router.go('/profile');
                                        } else {
                                          messenger.showSnackBar(
                                            SnackBar(
                                              content: Text(
                                                newState.errorMessage!,
                                              ),
                                            ),
                                          );
                                        }
                                      },
                                      child: const Text('Select'),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
